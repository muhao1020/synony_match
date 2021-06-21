更改了 synonym term，使用term type 判断出分词出来的是 同义词，更改权重
```json
PUT /test_synonym_1
{
  "settings": {
    "index": {
      "analysis": {
        "analyzer": {
          "match_ana": {
            "tokenizer": "whitespace",
            "filter": [
              "synonym",
              "my_custom_stop_words_filter"
            ]
          }
        },
        "filter": {
          "synonym": {
            "type": "synonym",
            "synonyms": [
              "class, school",
              "hello, nihao"
            ]
          },
          "my_custom_stop_words_filter": {
            "type": "stop",
            "ignore_case": true,
            "stopwords": [
              "and",
              "is",
              "the",
              "my"
            ]
          }
        }
      }
    }
  }
}

# title 添加了 synonym filter ，在index的时候加入 同义词
PUT test_synonym_1/_mapping
{
  "properties": {
    "title": {
      "type": "text",
      "analyzer": "match_ana"
    },
    "content" :{
      "type" :"text",
      "analyzer" :"whitespace" 
    }
  }
}


POST test_synonym_1/_doc/1
{
  "title":"this is my school",
  "content":"this is my school"
}

POST test_synonym_1/_doc/2
{
  "title":"this is my class",
  "content":"this is my class"
}

POST test_synonym_1/_doc/3
{
  "title":"the quick brown fox jumped over the lazy dog",
  "content":"the quick brown fox jumped over the lazy dog"
}

POST test_synonym_1/_doc/4
{
  "title":"what is your hello",
  "content":"what is your hello"
}

# 无论使用 term 还是 match，使用同一词字段可以全部召回
# 但是无法区分原始词，还是同义词召回，并且在评分上没有区别对待
GET test_synonym_1/_search
{
  "query": {
    "match": {
      "title": "class"
    }
  }
}

GET test_synonym_1/_search
{
  "query": {
    "term": {
      "title": {
        "value": "class"
      }
    }
  }
}


# 使用自定义的query 设置同义词产生的权重
# 必传参数 query 和 synonym_analyzer 
# query 搜索内容
# synonym_analyzer 分词器，可以为全局分词器，也可以为index分词器，但应该使用带 synonym filter的分词器
# zero_terms_query 表示如果query被synonym_analyzer分次之后为0个term，全都是停用词，那么召回策略是什么，参考 https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query.html#query-dsl-match-query-zero
# synonym_type_boost 表示同义词召回内容加入的权重，默认是 0.00001。
GET test_synonym_1/_search
{
  "explain": false,
  "query": {
    "synonym_match": {
      "content": {
        "query": "this class",
        "synonym_analyzer": "match_ana",
        "zero_terms_query": "none",
        "synonym_type_boost" : 0.00001
      }
    }
  }
}

```