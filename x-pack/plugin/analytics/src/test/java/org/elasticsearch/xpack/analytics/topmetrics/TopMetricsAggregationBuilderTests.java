/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.analytics.topmetrics;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.search.aggregations.AggregationInitializationException;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.BaseAggregationBuilder;
import org.elasticsearch.search.aggregations.support.MultiValuesSourceFieldConfig;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.AbstractXContentSerializingTestCase;
import org.elasticsearch.test.AbstractXContentTestCase;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class TopMetricsAggregationBuilderTests extends AbstractXContentSerializingTestCase<TopMetricsAggregationBuilder> {
    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        return new NamedWriteableRegistry(
            Arrays.asList(new NamedWriteableRegistry.Entry(SortBuilder.class, FieldSortBuilder.NAME, FieldSortBuilder::new))
        );
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        return new NamedXContentRegistry(
            Arrays.asList(
                new NamedXContentRegistry.Entry(
                    BaseAggregationBuilder.class,
                    new ParseField(TopMetricsAggregationBuilder.NAME),
                    (p, c) -> TopMetricsAggregationBuilder.PARSER.parse(p, (String) c)
                )
            )
        );
    }

    @Override
    protected TopMetricsAggregationBuilder doParseInstance(XContentParser parser) throws IOException {
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.START_OBJECT));
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.FIELD_NAME));
        String name = parser.currentName();
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.START_OBJECT));
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.FIELD_NAME));
        assertThat(parser.currentName(), equalTo("top_metrics"));
        TopMetricsAggregationBuilder parsed = TopMetricsAggregationBuilder.PARSER.apply(parser, name);
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.END_OBJECT));
        assertThat(parser.nextToken(), equalTo(XContentParser.Token.END_OBJECT));
        return parsed;
    }

    @Override
    protected Reader<TopMetricsAggregationBuilder> instanceReader() {
        return TopMetricsAggregationBuilder::new;
    }

    @Override
    protected TopMetricsAggregationBuilder createTestInstance() {
        List<SortBuilder<?>> sortBuilders = singletonList(
            new FieldSortBuilder(randomAlphaOfLength(5)).order(randomFrom(SortOrder.values()))
        );
        List<MultiValuesSourceFieldConfig> metricFields = InternalTopMetricsTests.randomMetricNames(between(1, 5)).stream().map(name -> {
            MultiValuesSourceFieldConfig.Builder metricField = new MultiValuesSourceFieldConfig.Builder();
            metricField.setFieldName(randomAlphaOfLength(5)).setMissing(1.0);
            return metricField.build();
        }).collect(toList());
        return new TopMetricsAggregationBuilder(randomAlphaOfLength(5), sortBuilders, between(1, 100), metricFields);
    }

    @Override
    protected TopMetricsAggregationBuilder mutateInstance(TopMetricsAggregationBuilder instance) {
        return null;// TODO implement https://github.com/elastic/elasticsearch/issues/25929
    }

    public void testClientBuilder() throws IOException {
        AbstractXContentTestCase.xContentTester(this::createParser, this::createTestInstance, this::toXContentThroughClientBuilder, p -> {
            p.nextToken();
            AggregatorFactories.Builder b = AggregatorFactories.parseAggregators(p);
            assertThat(b.getAggregatorFactories(), hasSize(1));
            assertThat(b.getPipelineAggregatorFactories(), empty());
            return (TopMetricsAggregationBuilder) b.getAggregatorFactories().iterator().next();
        }).test();
    }

    public void testValidation() {
        AggregationInitializationException e = expectThrows(AggregationInitializationException.class, () -> {
            List<SortBuilder<?>> sortBuilders = singletonList(
                new FieldSortBuilder(randomAlphaOfLength(5)).order(randomFrom(SortOrder.values()))
            );
            List<MultiValuesSourceFieldConfig> metricFields = InternalTopMetricsTests.randomMetricNames(between(1, 5))
                .stream()
                .map(name -> {
                    MultiValuesSourceFieldConfig.Builder metricField = new MultiValuesSourceFieldConfig.Builder();
                    metricField.setFieldName(randomAlphaOfLength(5)).setMissing(1.0);
                    return metricField.build();
                })
                .collect(toList());
            new TopMetricsAggregationBuilder("tm", sortBuilders, between(1, 100), metricFields).subAggregations(
                AggregatorFactories.builder()
            );
        });
        assertEquals("Aggregator [tm] of type [top_metrics] cannot accept sub-aggregations", e.getMessage());
    }

    private void toXContentThroughClientBuilder(TopMetricsAggregationBuilder serverBuilder, XContentBuilder builder) throws IOException {
        builder.startObject();
        createClientBuilder(serverBuilder).toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
    }

    private org.elasticsearch.client.analytics.TopMetricsAggregationBuilder createClientBuilder(
        TopMetricsAggregationBuilder serverBuilder
    ) {
        assertThat(serverBuilder.getSortBuilders(), hasSize(1));
        return new org.elasticsearch.client.analytics.TopMetricsAggregationBuilder(
            serverBuilder.getName(),
            serverBuilder.getSortBuilders().get(0),
            serverBuilder.getSize(),
            serverBuilder.getMetricFields().stream().map(MultiValuesSourceFieldConfig::getFieldName).toArray(String[]::new)
        );
    }
}
