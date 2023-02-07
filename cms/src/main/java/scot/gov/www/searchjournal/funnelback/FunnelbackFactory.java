package scot.gov.www.searchjournal.funnelback;

import org.hippoecm.hst.core.container.ContainerConfiguration;
import org.hippoecm.hst.site.HstServices;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.www.searchjournal.FunnelbackReconciliationLoop;

public class FunnelbackFactory {

    private static final Logger LOG = LoggerFactory.getLogger(FunnelbackReconciliationLoop.class);

    private static final String ERROR_RATE_ATTRIBUTE = "errorRate";

    private static final String FILTERS_ATTRIBUTE = "filters";

    private FunnelbackFactory() {
        // hide implicit constructor
    }

    public static Funnelback newFunnelback(RepositoryJobExecutionContext context) {

        // decide what kind of funnelback instance to create based on the job context.
        String filters = filters(context);
        Funnelback funnelback = new FunnelbackImpl(configuration(), filters);

        LOG.info("filters: {}", filters);
        // for testing error conditions it is possible to configure the repository to add artificial errors to our
        // implementation of talking to funnelback
        double errorRate = 0.0;
        if (context.getAttributeNames().contains(ERROR_RATE_ATTRIBUTE)) {
            String errorRateString = context.getAttribute(ERROR_RATE_ATTRIBUTE);
            errorRate = Double.parseDouble(errorRateString);
        }

        if (errorRate > 0.0) {
            LOG.warn("WARNING, using FlakyFunnelback to simulate errors.  Error rate is {}", errorRate);
            funnelback = new FlakyFunnelback(funnelback, errorRate);
        }

        return new MetricsCollectingFunnelbackImpl(funnelback);
    }

    static FunnelbackConfiguration configuration() {
        FunnelbackConfiguration configuration = new FunnelbackConfiguration();
        ContainerConfiguration containerConfiguration = HstServices.getComponentManager().getContainerConfiguration();
        String url = containerConfiguration.getString("funnelback.url");
        String token = containerConfiguration.getString("funnelback.token");
        configuration.setApiUrl(url);
        configuration.setApiKey(token);

        return configuration;
    }

    static String filters(RepositoryJobExecutionContext context) {
        return context.getAttributeNames().contains(FILTERS_ATTRIBUTE)
                ? context.getAttribute(FILTERS_ATTRIBUTE) : "";
    }
}