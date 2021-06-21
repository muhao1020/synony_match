package org.elasticsearch.mysynonym;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.common.lucene.search.Queries.newUnmappedFieldQuery;

/**
 * @Classname SynonymMatchQuery
 * @Description TODO
 * @Date 2021/6/17 19:32
 * @Created by muhao
 */
public class SynonymMatchQuery {


    public enum ZeroTermsQuery implements Writeable {
        NONE(0),
        ALL(1),
        // this is used internally to make sure that query_string and simple_query_string
        // ignores query part that removes all tokens.
        NULL(2);

        private final int ordinal;

        ZeroTermsQuery(int ordinal) {
            this.ordinal = ordinal;
        }

        public static ZeroTermsQuery readFromStream(StreamInput in) throws IOException {
            int ord = in.readVInt();
            for (ZeroTermsQuery zeroTermsQuery : ZeroTermsQuery.values()) {
                if (zeroTermsQuery.ordinal == ord) {
                    return zeroTermsQuery;
                }
            }
            throw new ElasticsearchException("unknown serialized type [" + ord + "]");
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(this.ordinal);
        }
    }


    public static final float DEFAULT_SYNONYM_BOOST = 0.00001f;


    public static final ZeroTermsQuery DEFAULT_ZERO_TERMS_QUERY = ZeroTermsQuery.NONE;

    protected final QueryShardContext context;

    protected Analyzer analyzer;

    protected float synonym_type_boost;

    protected BooleanClause.Occur occur = BooleanClause.Occur.SHOULD;

    protected SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery = DEFAULT_ZERO_TERMS_QUERY;

    public SynonymMatchQuery(QueryShardContext context) {
        this.context = context;
    }

    public void setAnalyzer(String analyzerName) {
        this.analyzer = context.getMapperService().getIndexAnalyzers().get(analyzerName);
        if (analyzer == null) {
            throw new IllegalArgumentException("No analyzer found for [" + analyzerName + "]");
        }
    }

    public float getSynonym_type_boost() {
        return synonym_type_boost;
    }

    public void setSynonym_type_boost(float synonym_type_boost) {
        this.synonym_type_boost = synonym_type_boost;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void setOccur(BooleanClause.Occur occur) {
        this.occur = occur;
    }

    public void setZeroTermsQuery(SynonymMatchQuery.ZeroTermsQuery zeroTermsQuery) {
        this.zeroTermsQuery = zeroTermsQuery;
    }

    public Query parse(String fieldName, Object value) throws IOException {
        final MappedFieldType fieldType = context.fieldMapper(fieldName);
        if (fieldType == null) {
            return newUnmappedFieldQuery(fieldName);
        }
        Analyzer analyzer = getAnalyzer(fieldType);
        assert analyzer != null;


        return parseInternal(fieldName, fieldType, value.toString());
    }

    protected final Query parseInternal(String fieldName, MappedFieldType fieldType, String queryText) throws IOException {
        final Query query;
        // Use the analyzer to get all the tokens, and then build an appropriate
        // query based on the analysis chain.
        try (TokenStream source = analyzer.tokenStream(fieldName, queryText)) {
            query = createFieldQuery(source, fieldType, fieldName);
        } catch (IOException e) {
            throw new RuntimeException("Error analyzing query text", e);
        }
        return query == null ? zeroTermsQuery() : query;
    }

    private Query createFieldQuery(TokenStream source, MappedFieldType fieldType, String field) {

        // 收集每一个 position 上的 term (如果有同义词 ，会有多个 term)
        List<List<TermType>> positionTerms = new ArrayList<>();
        List<TermType> tmpTerms = null;
        // Build an appropriate query based on the analysis chain.
        try (CachingTokenFilter stream = new CachingTokenFilter(source)) {

            TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
            PositionIncrementAttribute posIncAtt = stream.addAttribute(PositionIncrementAttribute.class);
            PositionLengthAttribute posLenAtt = stream.addAttribute(PositionLengthAttribute.class);

            if (termAtt == null) {
                return null;
            }

            // phase 1: read through the stream and assess the situation:
            // counting the number of tokens/positions and marking if we have any synonyms.


            stream.reset();
            // 执行 stream.incrementToken() 会产生一个 term
            // TODO 检查 stop 词的情况
            while (stream.incrementToken()) {
                int positionIncrement = posIncAtt.getPositionIncrement();
                // 分词出下一个 term
                if (positionIncrement != 0 && tmpTerms != null) {
                    positionTerms.add(tmpTerms);
                    tmpTerms = null;
                }
                if (tmpTerms == null) {
                    tmpTerms = new ArrayList<>();
                }
                PackedTokenAttributeImpl t = ((PackedTokenAttributeImpl) termAtt);
                String type = t.type();
                tmpTerms.add(new TermType(field, type, t.getBytesRef()));
            }
            if (tmpTerms != null) {
                positionTerms.add(tmpTerms);
            }

            // phase 2: based on token count, presence of synonyms, and options
            // formulate a single term, boolean, or phrase.
            if (positionTerms.size() == 0) {
                return null;
            } else {
                return analyzeList(positionTerms);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error analyzing query text", e);
        }
    }

    private Query analyzeList(List<List<TermType>> positionTerms) {
        if (positionTerms.size() == 1) {
            List<TermType> termTypes = positionTerms.get(0);
            if (termTypes.size() == 1){
                // single term
                TermType termType = termTypes.get(0);
                return newTermQuery(termType);
            } else {
                // single term with synonym
                return newSynonymQuery(termTypes);
            }
        } else {
                return newBooleanQuery(positionTerms);
        }
    }

    private Query newTermQuery(TermType termType){
        return new TermQuery(new Term(termType.getField(), termType.getBytes()));
    }

    private Query newSynonymQuery(List<TermType> termTypes){
        SynonymQuery.Builder builder = new SynonymQuery.Builder(termTypes.get(0).getField());
        for(TermType t : termTypes){
            if ("SYNONYM".equals(t.getType())) {
                builder.addTerm(new Term(t.getField(), t.getBytes()), synonym_type_boost);
            } else {
                builder.addTerm(new Term(t.getField(), t.getBytes()));
            }
        }
        return builder.build();
    }

    private Query newBooleanQuery(List<List<TermType>> positionTerms) {
        BooleanQuery.Builder q = new BooleanQuery.Builder();
        for (List<TermType> tt : positionTerms){
            if (tt.size() == 1) {
                q.add(newTermQuery(tt.get(0)), BooleanClause.Occur.SHOULD);
            } else {
                q.add(newSynonymQuery(tt), BooleanClause.Occur.SHOULD);
            }
        }
        return q.build();
    }


    protected Analyzer getAnalyzer(MappedFieldType fieldType) {
        if (analyzer == null) {
            return context.getSearchAnalyzer(fieldType);
        } else {
            return analyzer;
        }
    }

    protected Query zeroTermsQuery() {
        switch (zeroTermsQuery) {
            case NULL:
                return null;
            case NONE:
                return Queries.newMatchNoDocsQuery("Matching no documents because no terms present");
            case ALL:
                return Queries.newMatchAllQuery();
            default:
                throw new IllegalStateException("unknown zeroTermsQuery " + zeroTermsQuery);
        }
    }

}
