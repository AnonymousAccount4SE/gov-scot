package scot.gov.www.components;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.hst.container.RequestContextProvider;
import org.hippoecm.hst.content.beans.query.HstQuery;
import org.hippoecm.hst.content.beans.query.HstQueryResult;
import org.hippoecm.hst.content.beans.query.builder.Constraint;
import org.hippoecm.hst.content.beans.query.builder.HstQueryBuilder;
import org.hippoecm.hst.content.beans.query.exceptions.QueryException;
import org.hippoecm.hst.content.beans.standard.HippoBean;
import org.hippoecm.hst.core.component.HstRequest;
import org.hippoecm.hst.core.component.HstResponse;
import org.hippoecm.hst.core.parameters.ParametersInfo;
import org.hippoecm.hst.core.request.ComponentConfiguration;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.util.SearchInputParsingUtils;
import org.onehippo.cms7.essentials.components.EssentialsListComponent;
import org.onehippo.cms7.essentials.components.info.EssentialsListComponentInfo;
import org.onehippo.cms7.essentials.components.paging.Pageable;
import org.onehippo.forge.selection.hst.contentbean.ValueList;
import org.onehippo.forge.selection.hst.util.SelectionUtil;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;
import static org.hippoecm.hst.content.beans.query.builder.ConstraintBuilder.*;
import static scot.gov.www.components.FilteredResultsComponent.PUBLICATION_TYPES;

@ParametersInfo(type = EssentialsListComponentInfo.class)
public class SearchResultsComponent extends EssentialsListComponent {


    private static Collection<String> FIELD_NAMES = new ArrayList<>();
    private final ValueList publicationValueList =
            SelectionUtil.getValueListByIdentifier(PUBLICATION_TYPES, RequestContextProvider.get());

    @Override
    public void init(ServletContext servletContext, ComponentConfiguration componentConfig) {
        super.init(servletContext, componentConfig);
        Collections.addAll(FIELD_NAMES, "govscot:title", "govscot:summary", "govscot:content", "hippostd:tags", 
                "govscot:incumbentTitle", "govscot:policyTags", "govscot:roleTitle", "govscot:twitter",
                "govscot:email", "govscot:website", "govscot:flickr", "govscot:facebook", "govscot:blog");
    }

    @Override
    public void doBeforeRender(final HstRequest request,
                               final HstResponse response) {

        super.doBeforeRender(request, response);

        Map<String, Set<String>> params = sanitiseParameterMap(request,
                request.getRequestContext().getServletRequest().getParameterMap());

        request.setAttribute("parameters", params);
        request.setAttribute("publicationTypes", SelectionUtil.valueListAsMap(publicationValueList));
    }

    @Override
    protected <T extends EssentialsListComponentInfo>
    HstQuery buildQuery(final HstRequest request, final T paramInfo, final HippoBean scope) {

        final int pageSize = getPageSize(request, paramInfo);
        final int page = getCurrentPage(request);
        final int offset = (page - 1) * pageSize;

        HstQueryBuilder builder = HstQueryBuilder.create(scope);
        return builder.where(constraints(request, null))
                .limit(pageSize)
                .offset(offset)
                .build();
    }

    @Override
    protected <T extends EssentialsListComponentInfo>
    Pageable<HippoBean> executeQuery(final HstRequest request, final T paramInfo, final HstQuery query) throws QueryException {
        final int pageSize = getPageSize(request, paramInfo);
        final int page = getCurrentPage(request);

        final HstQueryResult execute = query.execute();
        return getPageableFactory().createPageable(
                execute.getHippoBeans(),
                execute.getTotalSize(),
                pageSize,
                page);
    }

    private Map<String, Set<String>> sanitiseParameterMap(HstRequest request, Map<String, String[]> parameterMap) {
        if (parameterMap == null) {
            return null;
        }

        Map<String, Set<String>> sanitisedMap = new HashMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {

            if (equalsIgnoreCase("page", entry.getKey())) {
                continue;
            }

            Set<String> splitParamaters = splitParameters(request, entry.getKey());
            sanitisedMap.put(entry.getKey(), splitParamaters);
        }
        return sanitisedMap;
    }

    private Constraint constraints(HstRequest request, String searchField) {

        List<Constraint> constraints = new ArrayList<>();
        addTermConstraints(constraints, request);

        Constraint[] constraints1 = new Constraint[constraints.size()];
        for (int i = 0; i < constraints.size(); i++) {
            constraints1[i] = constraints.get(i);
        }

        return and(constraints1);
    }

    private void addTermConstraints(List<Constraint> constraints, HstRequest request) {
        String term = param(request, "term");
        String parsedTerm = SearchInputParsingUtils.parse(term, false);

        if (StringUtils.isBlank(term)) {
            return;
        }
        constraints.add(or(fieldConstraints(parsedTerm)));
    }

    private Constraint [] fieldConstraints(String term) {

        List<Constraint> constraints = FIELD_NAMES
                .stream()
                .map(field -> constraint(field).contains(term))
                .collect(toList());
        return constraints.toArray(new Constraint[constraints.size()]);
    }

    private String param(HstRequest request, String param) {
        HstRequestContext requestContext = request.getRequestContext();
        HttpServletRequest servletRequest = requestContext.getServletRequest();
        return servletRequest.getParameter(param);
    }

    private Set<String> splitParameters(HstRequest request, String parameter) {
        String parameters = param(request, parameter);
        if (parameters == null) {
            return Collections.emptySet();
        }
        String [] topicTitleArray = parameters.split("\\;");
        return new HashSet<>(asList(topicTitleArray));
    }
}