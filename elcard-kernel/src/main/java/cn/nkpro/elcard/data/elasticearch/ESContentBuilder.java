/*
 * This file is part of ELCard.
 *
 * ELCard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ELCard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with ELCard.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.nkpro.elcard.data.elasticearch;

import cn.nkpro.elcard.basic.NkProperties;
import cn.nkpro.elcard.data.elasticearch.annotation.*;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

class ESContentBuilder {

    @Autowired
    private NkProperties properties;

    public String  parseDocument(Class<? extends AbstractESModel> esType){
        ESDocument document = esType.getAnnotation(ESDocument.class);
        Assert.notNull(document,String.format("类型 %s 的 ESDocument 注解不存在",esType.getName()));
        return document.value();
    }

    String parseDocId(AbstractESModel doc){
        Class<? extends AbstractESModel> esType = doc.getClass();
        // 获取ID的值，如果ID没有定义，则使用es默认的规则生成ID
        return Arrays.stream(esType.getDeclaredFields())
                .filter(field -> field.getAnnotation(ESId.class) != null)
                .sorted(Comparator.comparing(Field::getName))
                .map(field -> {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(doc);
                        return value==null? StringUtils.EMPTY:value.toString();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return StringUtils.EMPTY;
                    }
                }).collect(Collectors.joining("$"));
    }

    XContentBuilder buildNgramTokenizer() throws IOException {
        final XContentBuilder settings = XContentFactory.jsonBuilder();
        settings.startObject();
        {
            settings.startObject("analysis");
            {
                settings.startObject("analyzer");
                {
                    settings.startObject("ngram_analyzer");
                    settings.field("tokenizer","ngram_tokenizer");
                    settings.endObject();
                }
                settings.endObject();

                settings.startObject("tokenizer");
                {
                    settings.startObject("ngram_tokenizer");
                    settings.field("type","ngram");
                    settings.field("min_gram",4);
                    settings.field("max_gram",4);
                    settings.array("token_chars","letter","digit");
                    settings.endObject();
                }
                settings.endObject();
            }
            settings.endObject();
        }
        settings.endObject();
        return settings;
    }

    XContentBuilder buildMapping(Class<? extends AbstractESModel> esType) throws IOException {

        final XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            ESDynamicTemplate[] array = null;
            ESDynamicTemplates dynamicTemplates = esType.getAnnotation(ESDynamicTemplates.class);
            ESDynamicTemplate dynamicTemplate = esType.getAnnotation(ESDynamicTemplate.class);
            if(dynamicTemplates!=null){
                array = dynamicTemplates.value();
            }else if(dynamicTemplate!=null){
                array = new ESDynamicTemplate[]{dynamicTemplate};
            }

            if(array!=null){
                builder.startArray("dynamic_templates");
                for(ESDynamicTemplate template : array){
                    builder.startObject();
                    builder.startObject(template.value());
                    {
                        if(StringUtils.isNotBlank(template.matchMappingType()))
                            builder.field("match_mapping_type",template.matchMappingType());
                        if(StringUtils.isNotBlank(template.match()))
                            builder.field("match",template.match());
                        if(StringUtils.isNotBlank(template.unmatch()))
                            builder.field("unmatch",template.unmatch());

                        builder.startObject("mapping");
                        {
                            builder.field("type",template.mappingType().getValue());
                            if(template.analyzer()!=ESAnalyzerType.none)
                                builder.field("analyzer",template.analyzer());
                            if(template.searchAnalyzer()!=ESAnalyzerType.none)
                                builder.field("search_analyzer",template.searchAnalyzer());
                            if (template.copyToKeyword()) {
                                builder.field("copy_to", "$keyword");
                            }
                            if (template.original()) {
                                builder.startObject("fields");
                                builder.startObject("original");
                                builder.field("type", "keyword");
                                builder.endObject();
                                builder.endObject();
                            }
                            if(StringUtils.isNotBlank(template.format())){
                                builder.field("format",template.format());
                            }
                            for(@SuppressWarnings("unused") ESField field : template.fields()){
                                throw new RuntimeException("暂不支持");
                            }
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                    builder.endObject();
                }
                builder.endArray();
            }

            builder.startObject("properties");
            {
                Class<?> type = esType;
                do{
                    Field[] fields = type.getDeclaredFields();
                    for (Field field : fields) {
                        ESField esField = field.getAnnotation(ESField.class);
                        if (esField != null) {
                            builder.startObject(field.getName());
                            {
                                builder.field("type", esField.type().getValue());
                                if (esField.analyzer() != ESAnalyzerType.none) {
                                    builder.field("analyzer", esField.analyzer());
                                }
                                if (esField.searchAnalyzer() != ESAnalyzerType.none) {
                                    builder.field("search_analyzer", esField.searchAnalyzer());
                                }
                                //if (esField.fieldData()) {
                                //    builder.field("fielddata", true);
                                //}
                                if (esField.original()) {
                                    builder.startObject("fields");
                                    builder.startObject("original");
                                    builder.field("type", "keyword");
                                    builder.endObject();
                                    builder.endObject();
                                }
                                if (esField.copyToKeyword()) {
                                    builder.field("copy_to", "$keyword");
                                }
                                if(StringUtils.isNotBlank(esField.format())){
                                    builder.field("format",esField.format());
                                }
                            }
                            builder.endObject();
                        }
                    }
                }while ((type = type.getSuperclass())!=Object.class);
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    String documentIndex(String indexName){

        String prefix = properties.getEnvKey();
        if(StringUtils.isNotBlank(prefix)){
            return String.format("%s-%s",prefix,indexName);
        }

        return indexName;
    }

    String getIndexPrefix(){
        String prefix = properties.getEnvKey();
        if(StringUtils.isNotBlank(prefix)){
            return String.format("%s-",prefix);
        }
        return StringUtils.EMPTY;
    }
}
