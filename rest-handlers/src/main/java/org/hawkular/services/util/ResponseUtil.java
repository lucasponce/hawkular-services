package org.hawkular.services.util;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.util.JsonUtil.toJson;
import static org.hawkular.util.Util.isEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.hawkular.paging.Link;
import org.hawkular.paging.Order;
import org.hawkular.paging.Page;
import org.hawkular.paging.PageContext;
import org.hawkular.paging.Pager;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ResponseUtil {
    public final static String ACCEPT = "Accept";
    public final static String CONTENT_TYPE = "Content-Type";
    public final static String APPLICATION_JSON = "application/json";
    public static final String TENANT_HEADER_NAME = "Hawkular-Tenant";
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_PER_PAGE = "per_page";
    public static final String PARAM_SORT = "sort";
    public static final String PARAM_ORDER = "order";
    public static final String PARAM_IGNORE_UNKNOWN_QUERY_PARAMS = "ignoreUnknownQueryParams";
    public static final Collection<String> PARAMS_PAGING;
    static {
        PARAMS_PAGING = Arrays.asList(PARAM_PAGE, PARAM_PER_PAGE, PARAM_SORT, PARAM_ORDER);
    }

    public static class ApiError {

        @JsonInclude
        private final String errorMsg;

        public ApiError(String errorMsg) {
            this.errorMsg = errorMsg != null && !errorMsg.trim().isEmpty() ? errorMsg : "No details";
        }

        public String getErrorMsg() {
            return errorMsg;
        }
    }

    public static class ApiDeleted {

        @JsonInclude
        private final Integer deleted;

        public ApiDeleted(Integer deleted) {
            this.deleted = deleted != null  ? deleted : 0;
        }

        public Integer getDeleted() {
            return deleted;
        }
    }

    public static void badRequest(RoutingContext routing, String errorMsg) {
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(BAD_REQUEST.code())
                .end(toJson(new ApiError(errorMsg)));
    }

    public static void internalServerError(RoutingContext routing, String errorMsg) {
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(INTERNAL_SERVER_ERROR.code())
                .end(toJson(new ApiError(errorMsg)));
    }

    public static void notFound(RoutingContext routing, String errorMsg) {
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(NOT_FOUND.code())
                .end(toJson(new ApiError(errorMsg)));
    }

    public static void ok(RoutingContext routing, Object o) {
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(OK.code())
                .end(toJson(o));
    }

    public static void ok(RoutingContext routing) {
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(OK.code())
                .end();
    }

    public static <T> void paginatedOk(RoutingContext routing, Page<T> page) {
        createPagingHeaders(routing, page);
        routing.response()
                .putHeader(ACCEPT, APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(OK.code())
                .end(toJson(page));
    }

    public static String tenant(RoutingContext routing) {
        return routing.request().getHeader(TENANT_HEADER_NAME);
    }

    public static <T> void createPagingHeaders(RoutingContext routing, Page<T> resultList) {

        String uri = routing.request().uri();

        PageContext pc = resultList.getPageContext();
        int page = pc.getPageNumber();

        List<Link> links = new ArrayList<>();

        if (pc.isLimited() && resultList.getTotalSize() > (pc.getPageNumber() + 1) * pc.getPageSize()) {
            int nextPage = page + 1;
            links.add(new Link("next", replaceQueryParam(uri, "page", String.valueOf(nextPage))));
        }

        if (page > 0) {
            int prevPage = page - 1;
            links.add(new Link("prev", replaceQueryParam(uri, "page", String.valueOf(prevPage))));
        }

        if (pc.isLimited() && pc.getPageSize() > 0) {
            long lastPage = resultList.getTotalSize() / pc.getPageSize();
            if (resultList.getTotalSize() % pc.getPageSize() == 0) {
                lastPage -= 1;
            }
            links.add(new Link("last", replaceQueryParam(uri, "page", String.valueOf(lastPage))));
        }

        StringBuilder linkHeader = new StringBuilder(new Link("current", uri).rfc5988String());

        //followed by the rest of the link defined above
        links.forEach((l) -> linkHeader.append(", ").append(l.rfc5988String()));

        routing.response().headers().remove("Link");
        routing.response().putHeader("Link", linkHeader.toString());
        routing.response().headers().remove("X-Total-Count");
        routing.response().putHeader("X-Total-Count", String.valueOf(resultList.getTotalSize()));
    }

    public static String replaceQueryParam(String uri, String param, String value) {
        boolean isQuestion = uri.indexOf('?') != -1;
        boolean isPresent = uri.indexOf(param + "=") != -1;
        if (!isQuestion) {
            return uri + "?" + param + "=" + value;
        }
        if (isPresent) {
            String paramToken = uri.substring(uri.indexOf(param));
            int separator = paramToken.indexOf('&');
            if (separator != -1) {
                paramToken = paramToken.substring(0, separator);
            }
            return uri.replace(paramToken, param + "=" + value);
        } else {
            return uri + "&" + param + "=" + value;
        }
    }

    public static Pager extractPaging(MultiMap params) {
        String pageS = params.get("page") == null ? null : params.get("page");
        String perPageS = params.get("per_page") == null ? null : params.get("per_page");
        List<String> sort = params.getAll("sort");
        List<String> order = params.getAll("order");

        int page = pageS == null ? 0 : Integer.parseInt(pageS);
        int perPage = perPageS == null ? PageContext.UNLIMITED_PAGE_SIZE : Integer.parseInt(perPageS);

        List<Order> ordering = new ArrayList<>();

        if (sort == null || sort.isEmpty()) {
            ordering.add(Order.unspecified());
        } else {
            for (int i = 0; i < sort.size(); ++i) {
                String field = sort.get(i);
                Order.Direction dir = Order.Direction.ASCENDING;
                if (order != null && i < order.size()) {
                    dir = Order.Direction.fromShortString(order.get(i));
                }

                ordering.add(Order.by(field, dir));
            }
        }
        return new Pager(page, perPage, ordering);
    }

    public static Map<String, String> parseTags(String tags) {
        if (isEmpty(tags)) {
            return null;
        }
        String[] tagTokens = tags.split(",");
        Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
        for (String tagToken : tagTokens) {
            String[] fields = tagToken.split("\\|");
            if (fields.length == 2) {
                tagsMap.put(fields[0], fields[1]);
            } else {
                throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
            }
        }
        return tagsMap;
    }

    @SuppressWarnings("unchecked")
    public static String parseTagQuery(Map<String, String> tags) {
        if (isEmpty(tags)) {
            return null;
        }
        StringBuilder tagQuery = new StringBuilder();
        Iterator it = tags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> tag = (Map.Entry<String, String>)it.next();
            tagQuery.append(tag.getKey());
            if (!"*".equals(tag.getValue())) {
                tagQuery.append(" = ").append("").append(tag.getValue());
            }
            if (it.hasNext()) {
                tagQuery.append(" or ");
            }
        }
        return tagQuery.toString();
    }

    public static void checkForUnknownQueryParams(MultiMap params, final Set<String> expected) {
        if (params.contains(PARAM_IGNORE_UNKNOWN_QUERY_PARAMS)) {
            return;
        }
        Set<String> unknown = params.names().stream()
                .filter(p -> !expected.contains(p))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            String message = "Unknown Query Parameter(s): " + unknown.toString();
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean checkTags(Map<String, String> tagsMap) {
        if (isEmpty(tagsMap)) {
            return true;
        }
        for (Map.Entry<String, String> entry : tagsMap.entrySet()) {
            if (isEmpty(entry.getKey()) || isEmpty(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static String checkTenant(RoutingContext routing) {
        String tenantId = tenant(routing);
        if (!isEmpty(tenantId)) {
            return tenantId;
        }
        throw new BadRequestException(TENANT_HEADER_NAME + " header is required");
    }

    public static Set<String> getTenants(String tenantId) {
        Set<String> tenantIds = new TreeSet<>();
        for (String t : tenantId.split(",")) {
            tenantIds.add(t);
        }
        return tenantIds;
    }

    @SuppressWarnings("unchecked")
    public static void result(RoutingContext routing, AsyncResult result) {
        if (result.succeeded()) {
            if (result.result() == null) {
                ok(routing);
                return;
            }
            if (result.result() instanceof Page) {
                paginatedOk(routing, (Page)result.result());
                return;
            }
            ok(routing, result.result());
        } else {
            if (result.cause() instanceof BadRequestException) {
                badRequest(routing, result.cause().getMessage());
                return;
            }
            if (result.cause() instanceof NotFoundException) {
                notFound(routing, result.cause().getMessage());
                return;
            }
            internalServerError(routing, result.cause().getMessage());
        }
    }

    public static class BadRequestException extends RuntimeException {

        public BadRequestException(String message) {
            super(message);
        }

        public BadRequestException(Throwable cause) {
            super(cause);
        }

        public BadRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InternalServerException extends RuntimeException {

        public InternalServerException(String message) {
            super(message);
        }

        public InternalServerException(Throwable cause) {
            super(cause);
        }

        public InternalServerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NotFoundException extends RuntimeException {

        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(Throwable cause) {
            super(cause);
        }

        public NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
