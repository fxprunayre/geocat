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

package org.fao.geonet.api.regions.metadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.domain.ISODate;
import org.fao.geonet.domain.ReservedOperation;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.datamanager.IMetadataSchemaUtils;
import org.fao.geonet.kernel.datamanager.IMetadataUtils;
import org.fao.geonet.kernel.region.Region;
import org.fao.geonet.kernel.region.Request;
import org.fao.geonet.kernel.search.SearchManager;
import org.fao.geonet.kernel.search.spatial.ErrorHandler;
import org.fao.geonet.kernel.search.spatial.SpatialIndexWriter;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.services.Utils;
import org.fao.geonet.api.regions.MetadataRegion;
import org.fao.geonet.util.GMLParsers;
import org.fao.geonet.utils.Log;
import org.fao.geonet.utils.Xml;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.xml.Parser;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.filter.Filter;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

import jeeves.server.context.ServiceContext;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 * TODO: A polygon may be exclusion and in such case should be substracted from the overall extent?
 */
public class MetadataRegionSearchRequest extends Request {

    public static final String PREFIX = "metadata:";
    private static final FindByNodeName EXTENT_FINDER = new FindByNodeName("EX_BoundingPolygon", "EX_GeographicBoundingBox", "polygon");
    ServiceContext context;
    private List<? extends MetadataRegionFinder> regionFinders = Lists.newArrayList(
        new FindRegionByXPath(), new FindRegionByGmlId(), new FindRegionByEditRef());
    private String id;
    private String label;
    private GeometryFactory factory;

    public MetadataRegionSearchRequest(ServiceContext context, GeometryFactory factory) {
        this.context = context;
        this.factory = factory;
    }

    @Override
    public Request label(String labelParam) {
        this.label = labelParam;
        return this;
    }

    @Override
    public Request categoryId(String categoryIdParam) {
        return this;
    }

    @Override
    public Request maxRecords(int maxRecordsParam) {
        return this;
    }

    @Override
    public Collection<Region> execute() throws Exception {
        if (label == null && id == null || (id != null && !id.startsWith(PREFIX))) {
            return Collections.emptySet();
        }
        List<Region> regions = new ArrayList<Region>();
        if (label != null) {
            loadAll(regions, Id.create(label));
        } else if (id != null) {
            String[] parts = id.split(":", 3);
            String mdId = parts[1];
            String id;
            if (parts.length > 2) {
                id = parts[2];
                loadOnly(regions, Id.create(mdId), id);
            } else {
                loadSpatialExtent(regions, Id.create(mdId));
            }
            if (regions.size() > 1) {
                regions = Collections.singletonList(regions.get(0));
            }
        }
        return regions;
    }

    private void loadOnly(List<Region> regions, Id mdId, String id) throws Exception {
        MetadataRegionFinder regionFinder = null;
        for (MetadataRegionFinder next : regionFinders) {
            if (next.accepts(id)) {
                regionFinder = next;
                break;
            }
        }

        if (regionFinder != null) {
            Element metadata = findMetadata(mdId, regionFinder.needsEditData());
            if (metadata != null) {
                regionFinder.findRegion(this, regions, mdId, id, metadata);
            }
        }
    }

    boolean findContainingGmdEl(List<Region> regions, Id mdId, Element el) throws Exception {
        Element parent = el;
        while (parent != null) {
            if (EXTENT_FINDER.matches(parent)) {
                regions.add(parseRegion(mdId, parent));
                return true;
            }
            parent = parent.getParentElement();
        }
        return false;
    }

    private void loadAll(List<Region> regions, Id id) throws Exception {
        Element metadata = findMetadata(id, false);
        if (metadata != null) {
            Iterator<?> extents = null;
            extents = descentOrSelf(metadata);
            while (extents.hasNext()) {
                Object object = extents.next();
                if (object instanceof Element) {
                    regions.add(parseRegion(id, (Element) object));
                }
            }
        }
    }

    private void loadSpatialExtent(List<Region> regions, Id id) throws Exception {
        Element metadata = findMetadata(id, false);
        if (metadata != null) {
            Path schemaDir = getSchemaDir(id);
            MultiPolygon geom = SpatialIndexWriter.getSpatialExtent(schemaDir, metadata, new SpatialExtentErrorHandler());
            MetadataRegion region = new MetadataRegion(id, null, geom);
            regions.add(region);
        }
    }

    private class SpatialExtentErrorHandler implements ErrorHandler {
        @Override
        public void handleParseException(Exception e, String gml) {
            Log.error(Geonet.SPATIAL, "Failed to convert gml to jts object: " + gml + "\n\t" + e.getMessage(), e);
        }

        @Override
        public void handleBuildException(Exception e, List<Polygon> polygons) {
            Log.error(Geonet.SPATIAL, "Failed to create a MultiPolygon from: " + polygons, e);
        }
    }

    Iterator<?> descentOrSelf(Element metadata) {
        Iterator<?> extents;
        if (EXTENT_FINDER.matches(metadata)) {
            extents = Collections.singletonList(metadata).iterator();
        } else {
            extents = metadata.getDescendants(EXTENT_FINDER);
        }
        return extents;
    }

    private double getCoordinateValue(String coord, Element bbox) {
        Element coordElement = bbox.getChild(coord, bbox.getNamespace());
        if (coordElement != null) {
            List children = coordElement.getChildren();
            if (children.size() == 1 && children.get(0) instanceof Element) {
                return Double.parseDouble(((Element) children.get(0)).getText());
            }
        }
        return 0;
    };

    Region parseRegion(Id mdId, Element extentObj) throws Exception {
        GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
        gc.getBean(DataManager.class).getEditLib().removeEditingInfo(extentObj);

        String id = null;
        Geometry geometry = null;
        Namespace objNamespace = extentObj.getNamespace();
        if ("polygon".equals(extentObj.getName())) {
            String gml = Xml.getString(extentObj);
            geometry = SpatialIndexWriter.parseGml(getParser(extentObj), gml);
        } else if ("EX_BoundingPolygon".equals(extentObj.getName())) {
            Element polygon = extentObj.getChild("polygon", objNamespace);
            String gml = Xml.getString(polygon);
            geometry = SpatialIndexWriter.parseGml(getParser(polygon), gml);

            Element xmlGeom = (Element) polygon.getChildren().get(0);
            String srs = xmlGeom.getAttributeValue("srsName");
            CoordinateReferenceSystem sourceCRS = DefaultGeographicCRS.WGS84;

            if (srs != null && !(srs.equals(""))) sourceCRS = CRS.decode(srs);
            // if we have an srs and its not WGS84 then transform to WGS84
            if (!CRS.equalsIgnoreMetadata(sourceCRS, DefaultGeographicCRS.WGS84)) {
                MathTransform tform = CRS.findMathTransform(sourceCRS, DefaultGeographicCRS.WGS84);
                geometry = (MultiPolygon) JTS.transform(geometry, tform);
            }
        } else if ("EX_GeographicBoundingBox".equals(extentObj.getName())) {
            double minx = getCoordinateValue("westBoundLongitude", extentObj);
            double maxx = getCoordinateValue("eastBoundLongitude", extentObj);
            double miny = getCoordinateValue("southBoundLatitude", extentObj);
            double maxy = getCoordinateValue("northBoundLatitude", extentObj);
            geometry = factory.toGeometry(new Envelope(minx, maxx, miny, maxy));
        }

        if (geometry != null) {
            Element element = extentObj.getChild("element", Geonet.Namespaces.GEONET);
            if (element != null) {
                id = element.getAttributeValue("ref");
            }
            return new MetadataRegion(mdId, id, geometry);
        } else {
            return null;
        }
    }

    private Parser getParser(Element polygon) {
        try {
            return GMLParsers.create(((Element) polygon.getChildren().get(0)));
        } catch (Exception e) {
            return GMLParsers.createGML();
        }
    }

    private Element findMetadata(Id id, boolean includeEditData) throws Exception {
        final DataManager dataManager = context.getBean(DataManager.class);
        String mdId = id.getMdId(context.getBean(SearchManager.class), dataManager);
        try {
            if (context.getBean(IMetadataUtils.class).exists(Integer.parseInt(mdId))) {
                Lib.resource.checkPrivilege(context, mdId, ReservedOperation.view);

                return dataManager.getMetadata(context, mdId, includeEditData, false, true);
            } else {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }

    }

    private Path getSchemaDir(Id id) throws Exception {
        final DataManager dataManager = context.getBean(DataManager.class);
        String mdId = id.getMdId(context.getBean(SearchManager.class), dataManager);
        String schemaId = dataManager.getMetadataSchema(mdId);
        final IMetadataSchemaUtils schemaUtils = context.getBean(IMetadataSchemaUtils.class);
        return schemaUtils.getSchemaDir(schemaId);
    }

    @Override
    public Request id(String regionId) {
        this.id = regionId;
        return this;
    }

    @Override
    public Optional<Long> getLastModified() throws Exception {
        if (id.startsWith(MetadataRegionSearchRequest.PREFIX)) {
            String[] idParts = id.substring(MetadataRegionSearchRequest.PREFIX.length()).split(":");

            SearchManager searchManager = this.context.getBean(SearchManager.class);
            DataManager dataManager = this.context.getBean(DataManager.class);
            final String mdId = MetadataRegionSearchRequest.Id.create(idParts[0]).getMdId(searchManager, dataManager);

            if (mdId != null) {
                final ISODate docChangeDate = searchManager.getDocChangeDate(mdId);
                if (docChangeDate != null) {
                    return Optional.of(docChangeDate.toDate().getTime());
                }
            }
        }
        return Optional.absent();
    }

    public static abstract class Id {

        protected String id;
        private String prefix;

        public Id(String prefix, String id) {
            this.id = id;
            this.prefix = prefix;
        }

        static Id create(String id) {
            if (id.toLowerCase().startsWith(MdId.PREFIX)) {
                return new MdId(id);
            } else if (id.toLowerCase().startsWith(Uuid.PREFIX)) {
                return new Uuid(id);
            } else {
                return new FileId(id);
            }
        }

        /**
         * Convert ID to the id for looking up the metadata in the database
         */
        public abstract String getMdId(SearchManager searchManager, DataManager dataManager) throws Exception;

        /**
         * Strip the identifier from the id and return the id
         */
        abstract String getId();

        public String getIdentifiedId() {
            return prefix + id;
        }
    }

    @Deprecated
    public static class FileId extends Id {

        private static final String PREFIX = "@fileId";

        public FileId(String id) {
            super(PREFIX, id);
        }

        @Override
        public String getMdId(SearchManager searchManager, DataManager dataManager) throws Exception {
            String mdId = Utils.lookupMetadataIdFromFileId(id, searchManager);

            if (mdId == null) {
                mdId = dataManager.getMetadataId(id);
            }
            return mdId;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    public static class MdId extends Id {

        private static final String PREFIX = "@id";

        public MdId(String id) {
            super(PREFIX, id.substring(PREFIX.length()));
        }

        @Override
        public String getMdId(SearchManager searchManager, DataManager dataManager) {
            return id;
        }

        @Override
        public String getId() {
            return id;
        }

    }

    @Deprecated
    public static class Uuid extends Id {

        private static final String PREFIX = "@uuid";

        public Uuid(String id) {
            super(PREFIX, id.substring(PREFIX.length()));
        }

        @Override
        public String getMdId(SearchManager searchManager, DataManager dataManager) throws Exception {
            return dataManager.getMetadataId(id);
        }

        @Override
        public String getId() {
            return null;
        }

    }

    private static final class FindByNodeName implements Filter {
        private static final long serialVersionUID = 1L;
        private String[] names;

        public FindByNodeName(String... names) {
            this.names = names;
        }

        @Override
        public boolean matches(Object obj) {
            if (obj instanceof Element) {
                Element el = (Element) obj;
                for (String name : this.names) {
                    if (el.getName().equals(name)) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
