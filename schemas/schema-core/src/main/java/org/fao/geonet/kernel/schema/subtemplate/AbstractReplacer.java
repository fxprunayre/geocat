/*
 * Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.kernel.schema.subtemplate;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.util.Version;
import org.fao.geonet.kernel.schema.subtemplate.Status.Failure;
import org.fao.geonet.utils.Xml;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractReplacer implements Replacer{

    public static final float MIN_FIT_TO_REPLACE = 1.1f;

    protected List<Namespace> namespaces;
    protected ManagersProxy managersProxy;
    protected ConstantsProxy constantsProxy;
    private QueryParser phraseQueryParser;

    public AbstractReplacer(List<Namespace> namespaces,
                            ManagersProxy managersProxy,
                            ConstantsProxy constantsProxy) {
        this.constantsProxy = constantsProxy;
        this.managersProxy = managersProxy;
        this.namespaces = namespaces;
    }

    public Status replaceAll(Element dataXml, String localXlinkUrlPrefix, IndexReader indexReader, String localisedCharacterStringLanguageCode, String lang) {
        phraseQueryParser = new QueryParser(Version.LUCENE_4_9, null, managersProxy.getAnalyzer(lang));
        List<?> nodes = null;
        try {
            nodes = Xml.selectNodes(dataXml, getElemXPath(), namespaces);
        } catch (JDOMException e) {
            return new Failure(String.format("%s- selectNodes JDOMEx: %s", getAlias(), getElemXPath()));
        }
        return nodes.stream()
                .map((element) -> replace((Element) element, localXlinkUrlPrefix, indexReader, localisedCharacterStringLanguageCode))
                .collect(Status.STATUS_COLLECTOR);
    }



    protected Status replace(Element element, String localXlinkUrlPrefix, IndexReader indexReader, String localisedCharacterStringLanguageCode) {
        synchronized (indexReader) {
        BooleanQuery query = new BooleanQuery();
        try {
            IndexSearcher searcher = buildSearcher(indexReader);
            addMandatoryClause(query, constantsProxy.getIndexFieldNamesIS_TEMPLATE(),"s");
            addMandatoryClause(query, constantsProxy.getIndexFieldNamesVALID(), "1");
            queryAddExtraClauses(query, element, localisedCharacterStringLanguageCode);
            TopDocs docs = searcher.search(query,10000);

            if (docs.getMaxScore() > MIN_FIT_TO_REPLACE) {
                String uuid = indexReader.document(docs.scoreDocs[0].doc).getFields("_uuid")[0].stringValue();

                element.removeContent();

                StringBuffer params = new StringBuffer(localXlinkUrlPrefix);
                params.append(uuid);
                params = xlinkAddExtraParams(element, params);

                element.setAttribute("uuidref", uuid);
                element.setAttribute("href",
                        params.toString(),
                        constantsProxy.getNAMESPACE_XLINK());
                return new Status();
            }
            return new Failure(String.format("%s-found no match for query: %s", getAlias(), query.toString()));
        } catch (Exception e) {
            return new Failure(String.format("%s-exception %s: %s", getAlias(), e.toString(), query.toString()));
        }}
    }

    private IndexSearcher buildSearcher(IndexReader indexReader) {
        IndexSearcher searcher = new IndexSearcher(indexReader);
        searcher.setSimilarity(new DefaultSimilarity() {
            @Override
            public float idf(long docFreq, long numDocs) {
                return 1;
            }
        });
        return searcher;
    }

    private void addMandatoryClause(BooleanQuery query, String fieldName, String value) {
        TermQuery subQuery = new TermQuery(new Term(fieldName, value));
        subQuery.setBoost(0);
        query.add(subQuery, BooleanClause.Occur.MUST);
    }

    protected abstract void queryAddExtraClauses(BooleanQuery query, Element element, String lang) throws Exception;

    protected void addWeightingClause(BooleanQuery query, String indexFieldNames, String value) {
        if (value.length() > 0) {
            Query phraseQuery = phraseQueryParser.createPhraseQuery(indexFieldNames, value);
            if (phraseQuery == null) {
                return;
            }
            query.add(phraseQuery, BooleanClause.Occur.SHOULD);
        }
        else
            query.add(new TermQuery(new Term(String.format("%sIsBlank",indexFieldNames), "blank")), BooleanClause.Occur.SHOULD);
    }

    protected String getFieldValue(Element elem, String path, String localisedCharacterStringLanguageCode) throws JDOMException {
        String value = Xml.selectString(elem, String.format("%s/gco:CharacterString", path), namespaces);
        if (value.length() > 0) {
            return value;
        }
        return Xml.selectString(elem, String.format("%s/gmd:PT_FreeText/gmd:textGroup/gmd:LocalisedCharacterString[@locale='%s']", path, localisedCharacterStringLanguageCode), namespaces);
    }

    public abstract String getAlias();

    protected abstract String getElemXPath();

    protected abstract StringBuffer xlinkAddExtraParams(Element element, StringBuffer params) throws JDOMException;

}
