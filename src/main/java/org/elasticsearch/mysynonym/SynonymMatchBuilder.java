package org.elasticsearch.mysynonym;


import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;

import java.io.IOException;
import java.util.Objects;

/**
 * Match query is a query that analyzes the text and constructs a query as the
 * result of the analysis.
 */
public class SynonymMatchBuilder extends AbstractQueryBuilder<SynonymMatchBuilder> {

    private static final String CUTOFF_FREQUENCY_DEPRECATION_MSG = "you can omit this option, " +
            "the [match] query can skip block of documents efficiently if the total number of hits is not tracked";

    /**
     * @deprecated Since max_optimization optimization landed in 7.0, normal MatchQuery
     *             will achieve the same result without any configuration.
     */
    public static final ParseField QUERY_FIELD = new ParseField("query");
    public static final ParseField ANALYZER_FIELD = new ParseField("synonym_analyzer");
    public static final ParseField SYNONYM_BOOST_FIELD = new ParseField("synonym_type_boost");
    public static final ParseField ZERO_TERMS_QUERY_FIELD = new ParseField("zero_terms_query");

    /** The name for the match query */
    public static final String NAME = "synonym_match";


    private final String fieldName;
    private final Object value;
    private String analyzer;
    protected SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery = SynonymMatchQuery.DEFAULT_ZERO_TERMS_QUERY;
    // 同义词贡献 得分 为 0 是理想的情况
    private float synonym_type_boost = SynonymMatchQuery.DEFAULT_SYNONYM_BOOST;



    public void setZeroTermsQuery(SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery) {
        this.zeroTermsQuery = zeroTermsQuery;
    }

    /**
     * Constructs a new match query.
     */
    public SynonymMatchBuilder(String fieldName, Object value) {
        if (fieldName == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires fieldName");
        }
        if (value == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires query value");
        }
        this.fieldName = fieldName;
        this.value = value;
    }

    /**
     * Read from a stream.
     */
    public SynonymMatchBuilder(StreamInput in) throws IOException {
        super(in);
        fieldName = in.readString();
        value = in.readGenericValue();
        zeroTermsQuery = SynonymMatchQuery.ZeroTermsQuery.readFromStream(in);
        // optional fields
        analyzer = in.readOptionalString();
        synonym_type_boost = in.readFloat();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeGenericValue(value);
        zeroTermsQuery.writeTo(out);
        // optional fields
        out.writeOptionalString(analyzer);
        out.writeFloat(synonym_type_boost);
    }

    /** Returns the field name used in this query. */
    public String fieldName() {
        return this.fieldName;
    }

    /** Returns the value used in this query. */
    public Object value() {
        return this.value;
    }

    /**
     * Sets query to use in case no query terms are available, e.g. after analysis removed them.
     * Defaults to {@link SynonymMatchQuery.ZeroTermsQuery#NONE}, but can be set to
     * {@link SynonymMatchQuery.ZeroTermsQuery#ALL} instead.
     */
    public SynonymMatchBuilder zeroTermsQuery(SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery) {
        if (zeroTermsQuery == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires zeroTermsQuery to be non-null");
        }
        this.zeroTermsQuery = zeroTermsQuery;
        return this;
    }

    /**
     * Returns the setting for handling zero terms queries.
     */
    public SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery() {
        return this.zeroTermsQuery;
    }


    /**
     * Explicitly set the analyzer to use. Defaults to use explicit mapping config for the field, or, if not
     * set, the default search analyzer.
     */
    public SynonymMatchBuilder analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    /** Get the analyzer to use, if previously set, otherwise {@code null} */
    public String analyzer() {
        return this.analyzer;
    }

    public SynonymMatchBuilder synonym_type_boost(float synonym_type_boost){
        this.synonym_type_boost = synonym_type_boost;
        return this;
    }

    public float synonym_type_boost(){
        return this.synonym_type_boost;
    }


    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.startObject(fieldName);

        builder.field(QUERY_FIELD.getPreferredName(), value);
        if (analyzer != null) {
            builder.field(ANALYZER_FIELD.getPreferredName(), analyzer);
        }
        builder.field(SYNONYM_BOOST_FIELD.getPreferredName(), synonym_type_boost);
        builder.field(ZERO_TERMS_QUERY_FIELD.getPreferredName(), zeroTermsQuery.toString());
        printBoostAndQueryName(builder);
        builder.endObject();
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        // validate context specific fields
        if (analyzer != null && context.getIndexAnalyzers().get(analyzer) == null) {
            throw new QueryShardException(context, "[" + NAME + "] analyzer [" + analyzer + "] not found");
        }

        SynonymMatchQuery matchQuery = new SynonymMatchQuery(context);
        if (analyzer != null) {
            matchQuery.setAnalyzer(analyzer);
        }
        matchQuery.setSynonym_type_boost(synonym_type_boost);
        matchQuery.setZeroTermsQuery(zeroTermsQuery);
        // TODO 这里需要产生 自定义的 termQuery , synonymQuery 、 booleanQuery , 等能被 lucene识别的 query
        Query query = matchQuery.parse(fieldName, value);
        return query;
//        return Queries.maybeApplyMinimumShouldMatch(query, minimumShouldMatch);
    }

    @Override
    protected boolean doEquals(SynonymMatchBuilder other) {
        return Objects.equals(fieldName, other.fieldName) &&
                Objects.equals(value, other.value) &&
                Objects.equals(analyzer, other.analyzer) &&
                Objects.equals(zeroTermsQuery, other.zeroTermsQuery) &&
                Objects.equals(synonym_type_boost, other.synonym_type_boost);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, value, analyzer, zeroTermsQuery, synonym_type_boost);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public static SynonymMatchBuilder fromXContent(XContentParser parser) throws IOException {
        String fieldName = null;
        Object value = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        float synonym_type_boost = SynonymMatchQuery.DEFAULT_SYNONYM_BOOST;
        String analyzer = null;
        SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery = SynonymMatchQuery.DEFAULT_ZERO_TERMS_QUERY;
        String queryName = null;
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                throwParsingExceptionOnMultipleFields(NAME, parser.getTokenLocation(), fieldName, currentFieldName);
                fieldName = currentFieldName;
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();
                    } else if (token.isValue()) {
                        if (QUERY_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            value = parser.objectText();
                        } else if (ANALYZER_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            analyzer = parser.text();
                        } else if (AbstractQueryBuilder.BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            boost = parser.floatValue();
                        } else if (SYNONYM_BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            synonym_type_boost = parser.floatValue();
                        } else if (ZERO_TERMS_QUERY_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            String zeroTermsValue = parser.text();
                            if ("none".equalsIgnoreCase(zeroTermsValue)) {
                                zeroTermsQuery = SynonymMatchQuery.ZeroTermsQuery.NONE;
                            } else if ("all".equalsIgnoreCase(zeroTermsValue)) {
                                zeroTermsQuery = SynonymMatchQuery.ZeroTermsQuery.ALL;
                            } else {
                                throw new ParsingException(parser.getTokenLocation(),
                                        "Unsupported zero_terms_query value [" + zeroTermsValue + "]");
                            }
                        } else if (AbstractQueryBuilder.NAME_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            queryName = parser.text();
                        } else {
                            throw new ParsingException(parser.getTokenLocation(),
                                    "[" + NAME + "] query does not support [" + currentFieldName + "]");
                        }
                    } else {
                        throw new ParsingException(parser.getTokenLocation(),
                                "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                    }
                }
            } else {
                throwParsingExceptionOnMultipleFields(NAME, parser.getTokenLocation(), fieldName, parser.currentName());
                fieldName = parser.currentName();
                value = parser.objectText();
            }
        }

        if (value == null) {
            throw new ParsingException(parser.getTokenLocation(), "No text specified for text query");
        }

        // TODO 这里需要做的是组件这些从请求中传递的参数，组成 Query
        SynonymMatchBuilder matchQueryBuilder = new SynonymMatchBuilder(fieldName, value);
        matchQueryBuilder.analyzer(analyzer);
        matchQueryBuilder.zeroTermsQuery(zeroTermsQuery);
        matchQueryBuilder.queryName(queryName);
        matchQueryBuilder.boost(boost);
        matchQueryBuilder.synonym_type_boost(synonym_type_boost);
        return matchQueryBuilder;
    }

}
