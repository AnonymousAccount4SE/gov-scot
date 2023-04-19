package scot.gov.www.searchjournal;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.onehippo.repository.scheduling.RepositoryJob;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.httpclient.HttpClientSource;
import scot.gov.publishing.searchjounal.FeatureFlag;
import scot.gov.publishing.searchjounal.SearchJournal;
import scot.gov.publishing.searchjounal.SearchJournalEntry;
import scot.gov.searchjournal.FunnelbackMetricRegistry;
import scot.gov.searchjournal.MetricName;
import scot.gov.www.searchjournal.funnelback.*;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Reconciliation loop to read the search journal index index content in funnelback.
 */
public class FunnelbackReconciliationLoop implements RepositoryJob {

    private static final Logger LOG = LoggerFactory.getLogger(FunnelbackReconciliationLoop.class);

    // dictates how often the journal position is stored in funnelback
    private int saveInterval = 100;

    // the maximum number of journal entries to fetch each time the job runs
    private int maxJournalEntriesToFetch = 2000;

    private RetryPolicy retryPolicy = new RetryPolicy();

    private Counter failureCounter = FunnelbackMetricRegistry.getInstance().counter(MetricName.FAILURES.getName());

    private final Meter failureMeter = FunnelbackMetricRegistry.getInstance().meter(MetricName.FAILURE_RATE.getName());

    private final Timer jobTimer = FunnelbackMetricRegistry.getInstance().timer(MetricName.JOB_TIMES.getName());

    @Override
    public void execute(RepositoryJobExecutionContext context) throws RepositoryException {

        if (!isReady()) {
            return;
        }

        Session session = context.createSystemSession();
        try {
            FeatureFlag featureFlag = new FeatureFlag(session, "FunnelbackReconciliationLoop");
            if (featureFlag.isEnabled()) {
                doExecute(context, session, featureFlag);
            }
        } catch (RepositoryException e) {
            LOG.error("RepositoryException during funnelback reconciliation", e);
            throw e;
        } finally {
            session.logout();
        }
    }

    static boolean isReady() {
        if (!pingUrlResponding()) {
            LOG.warn("Ping url not responding yet");
            return false;
        }

        return true;
    }

    void doExecute(RepositoryJobExecutionContext context, Session session, FeatureFlag featureFlag) throws RepositoryException {
        Funnelback funnelback = FunnelbackFactory.newFunnelback(context);
        CloseableHttpClient httpClient = HttpClientSource.newClient();

        try {
            fetchAndProcessPendingJournalEntries(funnelback, httpClient, session, featureFlag);
        } catch (FunnelbackException e) {
            LOG.error("FunnelbackException during funnelback reconciliation", e);
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOG.error("Failed to close http client", e);
            }
            funnelback.close();
        }
    }

    static boolean pingUrlResponding() {
        HttpGet request = new HttpGet("http://localhost:8080/site/ping");
        try (CloseableHttpClient httpClient = HttpClientSource.newClient()) {
            CloseableHttpResponse response = httpClient.execute(request);
            try {
                return response.getStatusLine().getStatusCode() == 200;
            } finally {
                IOUtils.closeQuietly(response, e -> LOG.warn("Failed to close ping request", e));
            }
        } catch (IOException e) {
            LOG.warn("Failed to fetch ping url", e);
        }

        return false;
    }

    void fetchAndProcessPendingJournalEntries(
            Funnelback funnelback,
            CloseableHttpClient httpClient,
            Session session,
            FeatureFlag featureFlag) throws RepositoryException, FunnelbackException {

        SearchJournal journal = new SearchJournal(session);
        Calendar journalPosition = funnelback.getJournalPosition();
        if (journalPosition == null) {
            LOG.info("No journal position found ... skipping this run.");
            return;
        }

        GregorianCalendar cal = (GregorianCalendar) journalPosition;
        ZonedDateTime zdt = cal.toZonedDateTime();
        LOG.info("Journal position is {}", zdt.toString());

        List<SearchJournalEntry> pendingEntries = journal.getPendingEntries(journalPosition, maxJournalEntriesToFetch);
        if (pendingEntries.isEmpty()) {
            LOG.info("No journal entries to process");
            return;
        }

        LOG.info("{} pending journal entries to process, journal position is {}", pendingEntries.size(), journalPosition);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Timer.Context timerContext = jobTimer.time();
        int count = processPendingEntries(pendingEntries, funnelback, httpClient, journal, featureFlag);
        timerContext.stop();
        stopWatch.stop();
        LOG.info("reconciliation loop took {} to process {} journal entries", stopWatch.getTime(), count);
    }

    int processPendingEntries(
            List<SearchJournalEntry> pendingEntries,
            Funnelback funnelback,
            CloseableHttpClient httpClient,
            SearchJournal journal,
            FeatureFlag featureFlag) throws FunnelbackException {


        Map<String, List<SearchJournalEntry>> pendingEntriesByUrl = new HashMap<>();
        for (SearchJournalEntry entry : pendingEntries) {
            pendingEntriesByUrl.putIfAbsent(entry.getUrl(), new ArrayList<>());
            pendingEntriesByUrl.get(entry.getUrl()).add(entry);
        }

        Calendar newJournalPosition = null;
        int count = 0;
        for (SearchJournalEntry entry : pendingEntries) {

            if (!featureFlag.isEnabled()) {
                LOG.info("Job has been disabled, finishing early");
                break;
            }

            if (moreRecentEntryForUrl(entry, pendingEntriesByUrl)) {
                LOG.info("more recent entries exits for {}, skipping", entry.getUrl());
                newJournalPosition = entry.getTimestamp();
            } else {
                processEntry(funnelback, httpClient, journal, entry);
                count++;
                newJournalPosition = entry.getTimestamp();
                periodicSave(funnelback, newJournalPosition, count);
            }
        }
        funnelback.storeJournalPosition(newJournalPosition);
        return count;
    }

    boolean moreRecentEntryForUrl(SearchJournalEntry entry, Map<String, List<SearchJournalEntry>> pendingEntriesByUrl) {
        List<SearchJournalEntry> entriesForUrl = pendingEntriesByUrl.get(entry.getUrl());
        SearchJournalEntry mostrecententry = entriesForUrl.get(entriesForUrl.size() - 1);
        return entriesForUrl.size() > 1 && entry.getTimestamp().before(mostrecententry.getTimestamp());
    }

    void processEntry(Funnelback funnelback, CloseableHttpClient httpClient, SearchJournal journal, SearchJournalEntry entry) {
        try {
            doProcessEntry(funnelback, httpClient, entry);
        } catch (IOException e) {
            LOG.error("Failed to fetch HTML for journal entry {} ", entry.getUrl(), e);
            handleFailure(entry, journal);
        } catch (FunnelbackException e) {
            LOG.error("Failed index content for {} ", entry.getUrl(), e);
            handleFailure(entry, journal);
        }
    }

    void doProcessEntry(Funnelback funnelback, CloseableHttpClient httpClient, SearchJournalEntry entry) throws IOException, FunnelbackException{
        LOG.info("processing {} {} {} {} attempt {}", ((GregorianCalendar)entry.getTimestamp()).toZonedDateTime(), entry.getAction(), entry.getUrl(), entry.getCollection(), entry.getAttempt());
        switch (entry.getAction()) {
            case "depublish":
                funnelback.depublish(entry.getCollection(), entry.getUrl());
                break;
            case "publish":
                String html = getHtml(entry, httpClient);
                if (html != null) {
                    funnelback.publish(entry.getCollection(), entry.getUrl(), html);
                }
                break;
            default:
                LOG.error("Unexpected action {}", entry.getAction());
        }
    }

    void periodicSave(Funnelback funnelback, Calendar position, int count) throws FunnelbackException {
        if (count % saveInterval == 0) {
            funnelback.storeJournalPosition(position);
        }
    }

    String getLocalUrl(String url) {
        return StringUtils.replace(url, "https://www.gov.scot/", "http://localhost:8080/site/");
    }

    String getHtml(SearchJournalEntry entry, CloseableHttpClient httpClient) throws IOException {
        String localUrl = getLocalUrl(entry.getUrl());
        HttpGet request = new HttpGet(localUrl);
        request.setHeader("X-Forwarded-Host", "www.gov.scot");
        CloseableHttpResponse response = httpClient.execute(request);

        try {
            // if this is not a OK response then LOG and return null
            if (response.getStatusLine().getStatusCode() != 200) {
                LOG.error("{} fetching {}", response.getStatusLine().getStatusCode(), localUrl);
                return null;
            } else {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity);
            }
        } finally {
            response.close();
        }
    }

    void handleFailure(SearchJournalEntry entry, SearchJournal journal) {
        try {
            doHandleFailure(entry, journal);
        } catch (RepositoryException e) {
            LOG.error("RepositoryException trying to create a new journal entry for a failure. ", e);
        }
    }

    void doHandleFailure(SearchJournalEntry entry, SearchJournal journal) throws RepositoryException {
        if (!retryPolicy.shouldRetry(entry)) {
            failureCounter.inc();
            failureMeter.mark();
            LOG.error("entry has reached max attempts: {}, {}", entry.getAction(), entry.getUrl());
            return;
        }

        LOG.info("reconciliation failed {} {} {}, attempt {}",
                entry.getAction(), entry.getCollection(), entry.getUrl(), entry.getAttempt());
        long newAttempt = entry.getAttempt() + 1;
        SearchJournalEntry newEntry = new SearchJournalEntry();
        newEntry.setUrl(entry.getUrl());
        newEntry.setAction(entry.getAction());
        newEntry.setCollection(entry.getCollection());
        newEntry.setAttempt(newAttempt);
        newEntry.setTimestamp(getNewTimestamp(entry));
        journal.record(newEntry);
    }

    Calendar getNewTimestamp(SearchJournalEntry entry) {
        long backoff = retryPolicy.getBackoffPeriodInMillis(entry);
        long newTime = System.currentTimeMillis() + backoff;
        Calendar newTimestamp = Calendar.getInstance();
        newTimestamp.setTimeInMillis(newTime);
        return newTimestamp;
    }

}