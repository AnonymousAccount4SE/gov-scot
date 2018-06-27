package scot.gov.www.scheduledjobs.sitemap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.time.DateFormatUtils;
import org.onehippo.repository.scheduling.RepositoryJob;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.ws.rs.client.*;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static scot.gov.www.scheduledjobs.sitemap.SitemapAssetsUtils.createOrUpdateResource;

/**
 *
 * Scheduled job to create requred sitemaps as assets.
 *
 * Works by first generating the sitemap index file and then by generating a sitemap for each folder that s a child
 * of the root content folder.  Urls are fetch from hst / site via a rest service in batches of 100.
 */

public class SitemapGeneratorJob implements RepositoryJob {

    private static final Logger LOG = LoggerFactory.getLogger(SitemapGeneratorJob.class);

    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";

    private static final String ROOT_URL = "https://www.beta.gov.scot/";

    private static final String REST_URL = "http://localhost:8080/site/rest/urls/";

    private Client restClient = ClientBuilder.newClient();

    private ObjectMapper objectMapper = new ObjectMapper();

    public void execute(RepositoryJobExecutionContext context) throws RepositoryException {
        LOG.info("Generating sitemap");

        Session session = null;
        try {
            session = context.createSystemSession();
            session.refresh(false);
            createOrUpdateResource(session, "", rootSitemap(session));

            Set<String> stoplist = new HashSet<>();
            Collections.addAll(stoplist, "valuelists");
            Node root = session.getNode("/content/documents/govscot");
            NodeIterator nodeIterator = root.getNodes();
            while (nodeIterator.hasNext()) {
                Node node = nodeIterator.nextNode();
                String name = node.getName();
                if (stoplist.contains(name) || !node.isNodeType("hippostd:folder")) {
                    continue;
                }

                byte [] urlset = urlset(session, "/content/documents/govscot/" + name);
                createOrUpdateResource(session, name, urlset);
            }

            // now create the root sitemap
            byte [] urlset = urlset(session, "/content/documents/govscot");
            createOrUpdateResource(session, "root", urlset);
        } catch (XMLStreamException | IOException | RepositoryException e) {
            LOG.error("Failed to write sitemap", e);
        } finally {
            if(session != null) {
                session.logout();
            }
        }
    }

    private byte [] rootSitemap(Session session) throws IOException, XMLStreamException, RepositoryException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out);
        writer.setDefaultNamespace(SITEMAP_NS);
        writer.writeStartDocument();
        writer.writeStartElement("sitemapindex");

        Node root = session.getNode("/content/documents/govscot");
        NodeIterator nodeIterator = root.getNodes();
        while (nodeIterator.hasNext()) {
            Node child = nodeIterator.nextNode();
            writer.writeStartElement("sitemap");
            writer.writeStartElement("loc");
            String url = format("%ssitemap.%s.xml", ROOT_URL, child.getName());
            writer.writeCharacters(url);
            writer.writeEndElement();
            writer.writeEndElement();
        }

        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
        out.close();

        return out.toByteArray();
    }

    private byte [] urlset(Session session, String path) throws RepositoryException, XMLStreamException, IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out);
        writer.setDefaultNamespace(SITEMAP_NS);
        writer.writeStartDocument();
        writer.writeStartElement("urlset");

        Map<String, SitemapEntry> entriesByLoc
                = mapSitemapEntriesByLoc(getPublishedNodesUnderPath(session, path));

        // convert the paths in the entries to urls using the rest service
        // partition them into chunks of 100 first
        Map<Integer, List<SitemapEntry>> partitions = partition(entriesByLoc.values());

        for (List<SitemapEntry> partition : partitions.values()) {
            UrlResponse urlResponse = fetchUrlsForPartition(restClient, partition);

            for (Map.Entry<String, String> pathAndUrl : urlResponse.getUrls().entrySet()) {
                SitemapEntry entry = entriesByLoc.get(pathAndUrl.getKey());
                entry.setLoc(pathAndUrl.getKey());
                writer.writeStartElement("url");
                writer.writeStartElement("loc");
                writer.writeCharacters(sitemapUrl(pathAndUrl.getValue()));
                writer.writeEndElement();
                writer.writeStartElement("lastmod");
                writer.writeCharacters(DateFormatUtils.ISO_DATE_TIME_ZONE_FORMAT.format(entry.getLastModified()));
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
        out.close();
        return out.toByteArray();
    }

    private String sitemapUrl(String path) {
        String url = format("%s%s", ROOT_URL, path);
        return removeEnd(url, "index");
    }

    private UrlResponse fetchUrlsForPartition(Client client, List<SitemapEntry> partition) throws IOException, JsonProcessingException {
        UrlRequest urlRequest = new UrlRequest();
        urlRequest.setPaths(partition.stream().map(SitemapEntry::getLoc).collect(toList()));
        String res = client.target(REST_URL)
                .request(APPLICATION_JSON)
                .post(Entity.json(objectMapper.writeValueAsString(urlRequest)))
                .readEntity(String.class);
        return objectMapper.readValue(res, UrlResponse.class);
    }

    private NodeIterator getPublishedNodesUnderPath(Session session, String path) throws RepositoryException {
        String sql = format(
                "SELECT * FROM hippo:document WHERE " +
                        "hippostd:state = 'published' AND " +
                        "hippostd:stateSummary = 'live' AND " +
                        "jcr:path LIKE '%s/%%'",
                path);
        QueryResult result = session.getWorkspace().getQueryManager().createQuery(sql, Query.SQL).execute();
        return result.getNodes();
    }

    private Map<String, SitemapEntry> mapSitemapEntriesByLoc(NodeIterator nodeIt) throws RepositoryException {
        Map<String, SitemapEntry> entries = new HashMap<>();
        while (nodeIt.hasNext()) {
            SitemapEntry entry = toSitemapEntry(nodeIt.nextNode());
            entries.put(entry.getLoc(), entry);
        }
        return entries;
    }

    private SitemapEntry toSitemapEntry(Node node) throws RepositoryException {
        SitemapEntry entry = new SitemapEntry();
        entry.setLoc(node.getPath());
        entry.setLastModified(node.getProperty("hippostdpubwf:lastModificationDate").getDate());
        return entry;
    }

    private Map<Integer, List<SitemapEntry>> partition(Collection<SitemapEntry> entries) {
        int[] count = new int[1];
        return entries.stream().collect(
                groupingBy(
                    user -> {
                        count[0]++;
                        return Math.floorDiv( count[0], 100 );
                    }
                ));
    }
}