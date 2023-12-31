/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.search;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

class ThrowingQueryBuilder extends AbstractQueryBuilder<ThrowingQueryBuilder> {
    public static final String NAME = "throw";

    private final long randomUID;
    private final RuntimeException failure;
    private final int shardId;
    private final String index;

    /**
     * Creates a {@link ThrowingQueryBuilder} with the provided <code>randomUID</code>.
     *
     * @param randomUID used solely for identification
     * @param failure what exception to throw
     * @param shardId what shardId to throw the exception. If shardId is less than 0, it will throw for all shards.
     */
    ThrowingQueryBuilder(long randomUID, RuntimeException failure, int shardId) {
        super();
        this.randomUID = randomUID;
        this.failure = failure;
        this.shardId = shardId;
        this.index = null;
    }

    /**
     * Creates a {@link ThrowingQueryBuilder} with the provided <code>randomUID</code>.
     *
     * @param randomUID used solely for identification
     * @param failure what exception to throw
     * @param index what index to throw the exception against (all shards of that index)
     */
    ThrowingQueryBuilder(long randomUID, RuntimeException failure, String index) {
        super();
        this.randomUID = randomUID;
        this.failure = failure;
        this.shardId = Integer.MAX_VALUE;
        this.index = index;
    }

    ThrowingQueryBuilder(StreamInput in) throws IOException {
        super(in);
        this.randomUID = in.readLong();
        this.failure = in.readException();
        this.shardId = in.readVInt();
        if (in.getTransportVersion().onOrAfter(TransportVersion.V_8_500_040)) {
            this.index = in.readOptionalString();
        } else {
            this.index = null;
        }
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeLong(randomUID);
        out.writeException(failure);
        out.writeVInt(shardId);
        if (out.getTransportVersion().onOrAfter(TransportVersion.V_8_500_040)) {
            out.writeOptionalString(index);
        }
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(SearchExecutionContext context) {
        final Query delegate = Queries.newMatchAllQuery();
        return new Query() {
            @Override
            public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
                if (context.getShardId() == shardId || shardId < 0 || context.index().getName().equals(index)) {
                    throw failure;
                }
                return delegate.createWeight(searcher, scoreMode, boost);
            }

            @Override
            public String toString(String field) {
                return delegate.toString(field);
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public void visit(QueryVisitor visitor) {
                visitor.visitLeaf(this);
            }
        };
    }

    @Override
    protected boolean doEquals(ThrowingQueryBuilder other) {
        return false;
    }

    @Override
    protected int doHashCode() {
        return 0;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.ZERO;
    }
}
