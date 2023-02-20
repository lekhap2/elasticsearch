/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.fetch.subphase.highlight;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.uhighlight.CustomUnifiedHighlighter;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.annotatedtext.AnnotatedTextFieldMapper.AnnotatedHighlighterAnalyzer;
import org.elasticsearch.index.mapper.annotatedtext.AnnotatedTextFieldMapper.AnnotatedText;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.fetch.FetchSubPhase.HitContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnnotatedTextHighlighter extends UnifiedHighlighter {

    public static final String NAME = "annotated";

    // Convert the marked-up values held on-disk to plain-text versions for highlighting
    @Override
    protected List<Object> loadFieldValues(
        CustomUnifiedHighlighter highlighter,
        QueryShardContext qsc,
        MappedFieldType fieldType,
        HitContext hitContext,
        boolean forceSource
    ) throws IOException {
        List<Object> fieldValues = super.loadFieldValues(highlighter, qsc, fieldType, hitContext, forceSource);

        List<Object> strings = new ArrayList<>(fieldValues.size());
        AnnotatedText[] annotations = new AnnotatedText[fieldValues.size()];
        for (int i = 0; i < fieldValues.size(); i++) {
            annotations[i] = AnnotatedText.parse(fieldValues.get(i).toString());
            strings.add(annotations[i].textMinusMarkup);
        }
        // Store the annotations in the formatter and analyzer
        ((AnnotatedPassageFormatter) highlighter.getFormatter()).setAnnotations(annotations);
        ((AnnotatedHighlighterAnalyzer) highlighter.getIndexAnalyzer()).setAnnotations(annotations);
        return strings;
    }

    @Override
    protected Analyzer wrapAnalyzer(Analyzer analyzer) {
        return new AnnotatedHighlighterAnalyzer(super.wrapAnalyzer(analyzer));
    }

    @Override
    protected PassageFormatter getPassageFormatter(HitContext hitContext, SearchHighlightContext.Field field, Encoder encoder) {
        return new AnnotatedPassageFormatter(encoder);
    }

}