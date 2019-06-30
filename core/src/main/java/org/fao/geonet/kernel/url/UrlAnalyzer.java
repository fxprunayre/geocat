package org.fao.geonet.kernel.url;

import org.fao.geonet.domain.AbstractMetadata;
import org.fao.geonet.domain.Link;
import org.fao.geonet.domain.LinkStatus;
import org.fao.geonet.domain.Metadata;
import org.fao.geonet.domain.MetadataLink;
import org.fao.geonet.domain.MetadataLinkId_;
import org.fao.geonet.domain.MetadataLink_;
import org.fao.geonet.kernel.SchemaManager;
import org.fao.geonet.kernel.schema.LinkAwareSchemaPlugin;
import org.fao.geonet.kernel.schema.LinkPatternStreamer.ILinkBuilder;
import org.fao.geonet.kernel.schema.SchemaPlugin;
import org.fao.geonet.repository.LinkRepository;
import org.fao.geonet.repository.LinkStatusRepository;
import org.fao.geonet.repository.MetadataLinkRepository;
import org.fao.geonet.repository.MetadataRepository;
import org.jdom.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.Collections;

import static java.util.Objects.isNull;
import static org.springframework.data.jpa.domain.Specifications.where;

public class UrlAnalyzer {

    @Autowired
    protected SchemaManager schemaManager;

    @Autowired
    protected MetadataRepository metadataRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    protected UrlChecker urlChecker;

    @Autowired
    protected LinkRepository linkRepository;

    @Autowired
    protected LinkStatusRepository linkStatusRepository;

    @Autowired
    protected MetadataLinkRepository metadataLinkRepository;

    public void init() {
        urlChecker= new UrlChecker();
    }

    public void processMetadata(Element element, AbstractMetadata md) throws org.jdom.JDOMException {
        SchemaPlugin schemaPlugin = schemaManager.getSchema(md.getDataInfo().getSchemaId()).getSchemaPlugin();
        if (schemaPlugin instanceof LinkAwareSchemaPlugin) {

            metadataLinkRepository
                    .findAll(metadatalinksTargetting(md))
                    .stream()
                    .forEach(metadataLinkRepository::delete);
            metadataLinkRepository.flush();

            ((LinkAwareSchemaPlugin) schemaPlugin).createLinkStreamer(new ILinkBuilder<Link, AbstractMetadata>() {
                @Override
                public Link build() {
                    return new Link();
                }

                @Override
                public void setUrl(Link link, String url) {
                    link.setUrl(url);
                }

                @Override
                public void persist(Link link, AbstractMetadata metadata) {
                    final Link existingLink = linkRepository.findOneByUrl(link.getUrl());
                    if (existingLink == null) {
                        linkRepository.save(link);
                    }
                    MetadataLink metadataLink = new MetadataLink();
                    metadataLink.setId(metadataRepository.findOne(metadata.getId()),
                        existingLink == null ? link : existingLink
                    );
                    metadataLinkRepository.save(metadataLink);
                }
            }).processAllRawText(element, md);
            entityManager.flush();
        }
    }

    public void purgeMetataLink(Link link) {
        entityManager.detach(link);
        metadataLinkRepository
                .findAll(metadatalinksTargetting(link))
                .stream()
                .filter(metadatalink -> isReferencingAnUnknownMetadata((MetadataLink)metadatalink))
                .forEach(metadataLinkRepository::delete);
    }

    public void testLink(Link link) {
        LinkStatus linkStatus = urlChecker.getUrlStatus(link.getUrl());
        linkStatus.setLinkId(link.getId());
        linkStatusRepository.save(linkStatus);
    }

    private Specification<MetadataLink> metadatalinksTargetting(Link link) {
        return new Specification<MetadataLink>() {
            @Override
            public Predicate toPredicate(Root<MetadataLink> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get(MetadataLink_.id).get(MetadataLinkId_.linkId), link.getId());
            }
        };
    }

    private Specification<MetadataLink> metadatalinksTargetting(AbstractMetadata md) {
        return new Specification<MetadataLink>() {
            @Override
            public Predicate toPredicate(Root<MetadataLink> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get(MetadataLink_.id).get(MetadataLinkId_.metadataId), md.getId());
            }
        };
    }

    private boolean isReferencingAnUnknownMetadata(MetadataLink metadatalink) {
        return isNull(metadataRepository.findOne(metadatalink.getId().getMetadataId()));
    }
}
