<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:ili="http://www.interlis.ch/INTERLIS2.3"
                xmlns:che="http://www.geocat.ch/2008/che"
                xmlns:gco="http://www.isotc211.org/2005/gco"
                xmlns:gmd="http://www.isotc211.org/2005/gmd"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:gts="http://www.isotc211.org/2005/gts"
                xmlns:util="java:org.fao.geonet.util.XslUtil"
                exclude-result-prefixes="che gco gmd gts util">

    <xsl:template mode="MaintenanceInfo" match="che:CHE_MD_MaintenanceInformation|gmd:MD_MaintenanceInformation">
        <xsl:param name="backRef"/>
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformation TID="x{util:randomId()}">
            <xsl:apply-templates mode="text" select="gmd:maintenanceAndUpdateFrequency"/>
            <xsl:apply-templates mode="text" select="gmd:dateOfNextUpdate"/>
            <xsl:apply-templates mode="MaintenanceInfo" select="gmd:userDefinedMaintenanceFrequency"/>
            <xsl:apply-templates mode="enum" select="gmd:updateScope"/>
            <xsl:apply-templates mode="text" select="che:dateOfMonitoringState"/>
            <xsl:apply-templates mode="groupText" select=".">
                <xsl:with-param name="element">maintenanceNote</xsl:with-param>
            </xsl:apply-templates>
            <ili:BACK_REF name="{$backRef}"/>

            <xsl:apply-templates mode="MaintenanceInfo" select="gmd:updateScopeDescription"/>
            <xsl:apply-templates mode="MaintenanceInfo" select="gmd:contact"/>
            <xsl:apply-templates mode="MaintenanceInfo" select="che:historyConcept/che:CHE_MD_HistoryConcept"/>
            <xsl:apply-templates mode="MaintenanceInfo" select="che:archiveConcept/che:CHE_MD_ArchiveConcept"/>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformation>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="gmd:updateScopeDescription" >
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformationupdateScopeDescription TID="x{util:randomId()}">
            <xsl:apply-templates mode="MaintenanceInfo" select="gmd:MD_ScopeDescription"/>
            <ili:BACK_REF name="MD_MaintenanceInformation"/>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformationupdateScopeDescription>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="gmd:MD_ScopeDescription" >
        <ili:updateScopeDescription REF="?">
            <ili:GM03_2_1Core.Core.MD_ScopeDescription  TID="x{util:randomId()}">
                <xsl:apply-templates mode="ObjRef" select="gmd:attributes"/>
                <xsl:apply-templates mode="ObjRef" select="gmd:feature"/>
                <xsl:apply-templates mode="ObjRef" select="gmd:featureInstances"/>
                <xsl:apply-templates mode="ObjRef" select="gmd:attributeInstances"/>
                <xsl:apply-templates mode="text" select="gmd:dataset"/>
                <xsl:apply-templates mode="text" select="gmd:other"/>
            </ili:GM03_2_1Core.Core.MD_ScopeDescription>
        </ili:updateScopeDescription>
    </xsl:template>

    <xsl:template mode="ObjRef" match="*">
        <!-- CHECKME : is Interlis NAME datatype is multilingual ? -->
        <xsl:element name="{local-name()}"><xsl:value-of select="@uuidref|gco:CharacterString"/></xsl:element>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="che:historyConceptCitation" >
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_HistoryConcepthistoryConceptCitation TID="x{util:randomId()}">
            <ili:BACK_REF name="MD_HistoryConcept"/>
            <ili:historyConceptCitation REF="?">
               <ili:GM03_2_1Comprehensive.Comprehensive.CI_Citation TID="x2{generate-id(.)}">
                   <xsl:apply-templates mode="RefSystem"/>
               </ili:GM03_2_1Comprehensive.Comprehensive.CI_Citation>
            </ili:historyConceptCitation>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_HistoryConcepthistoryConceptCitation>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="che:CHE_MD_HistoryConcept">
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_HistoryConcept TID="x{util:randomId()}">
            <xsl:apply-templates mode="MaintenanceInfo" select="che:historyConceptCitation"/>
            <xsl:apply-templates mode="text" select="che:historyConceptURL"/>
            <ili:BACK_REF name="MD_MaintenanceInformation"/>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_HistoryConcept>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="che:archiveConceptCitation">
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_ArchiveConceptarchiveConceptCitation TID="x{util:randomId()}">
            <ili:BACK_REF name="MD_ArchiveConcept"/>
            <ili:archiveConceptCitation REF="?">
               <ili:GM03_2_1Comprehensive.Comprehensive.CI_Citation TID="x{util:randomId()}">
                   <xsl:apply-templates mode="RefSystem"/>
               </ili:GM03_2_1Comprehensive.Comprehensive.CI_Citation>
            </ili:archiveConceptCitation>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_ArchiveConceptarchiveConceptCitation>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="che:CHE_MD_ArchiveConcept">
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_ArchiveConcept TID="x{util:randomId()}">
            <xsl:apply-templates mode="MaintenanceInfo" select="che:archiveConceptCitation"/>
            <xsl:apply-templates mode="MaintenanceInfo" select="che:historyConceptCitation"/>
            <xsl:apply-templates mode="text" select="che:archiveConceptURL"/>
            <ili:BACK_REF name="MD_MaintenanceInformation"/>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_ArchiveConcept>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="gmd:contact">
        <ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformationcontact TID="x{util:randomId()}">
            <ili:contact REF="?">
                <xsl:apply-templates mode="RespParty"/>
            </ili:contact>
            <ili:BACK_REF name="MD_MaintenanceInformation"/>
            <xsl:apply-templates mode="RespPartyRole" select="che:CHE_CI_ResponsibleParty|gmd:CI_ResponsibleParty"/>
        </ili:GM03_2_1Comprehensive.Comprehensive.MD_MaintenanceInformationcontact>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="gmd:userDefinedMaintenanceFrequency">
        <xsl:apply-templates mode="MaintenanceInfo"/>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="gts:TM_PeriodDuration">
        <ili:userDefinedMaintenanceFrequency>
            <xsl:value-of select="concat(substring-before(substring-after(.,'P'),'Y'),':',substring-before(substring-after(.,'Y'),'M'),':',substring-before(substring-after(.,'M'),'D'),':',substring-before(substring-after(.,'T'),'H'),':',substring-before(substring-after(.,'H'),'M'),':',substring-before(substring-after(substring-after(.,'M'),'M'),'S'))"/>
        </ili:userDefinedMaintenanceFrequency>
    </xsl:template>

    <xsl:template mode="MaintenanceInfo" match="*" priority="-100">
        <ili:ERROR>Unknown MaintenanceInfo element <xsl:value-of select="local-name(.)"/></ili:ERROR>
    </xsl:template>
</xsl:stylesheet>