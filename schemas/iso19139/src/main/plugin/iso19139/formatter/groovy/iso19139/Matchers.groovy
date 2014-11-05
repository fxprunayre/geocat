package iso19139

public class Matchers {
    def handlers;
    def f
    def env

    def isUrlEl = {!it.'gmd:URL'.text().isEmpty()}
    def simpleElements = ['gco:Decimal', 'gco:Integer', 'gco:Scale', 'gco:Angle', 'gco:Measure', 'gco:Distance',
                          'gmd:MD_PixelOrientationCode', 'gts:TM_PeriodDuration']

    def skipContainers = ['gmd:CI_Series', 'gmd:MD_ReferenceSystem']

    def isSimpleEl = {el ->
        el.children().size() == 1 && simpleElements.any{!el[it].text().isEmpty()}
    }
    def isDateEl = {!it.'gco:DateTime'.text().isEmpty() || !it.'gco:Date'.text().isEmpty()}
    def hasDateChild = {it.children().size() == 1 && it.children().any(isDateEl)}
    def isCodeListEl = {!it['@codeListValue'].text().isEmpty()}
    def hasCodeListChild = {it.children().size() == 1 && it.children().any(isCodeListEl)}

    def isTextEl = {el ->
        !el.'gco:CharacterString'.text().isEmpty() ||
                !el.'gco:PT_FreeText'.'gco:textGroup'.'gmd:LocalisedCharacterString'.text().isEmpty()
    }

    def isContainerEl = {el ->
        !isTextEl(el) && !isUrlEl(el) &&
                !isCodeListEl(el) && !hasCodeListChild(el) &&
                !isDateEl(el) && !hasDateChild(el) &&
                !el.children().isEmpty()
                //!excludeContainer.any{it == el.name()}
    }

    def isRespParty = { el ->
        !el.'gmd:CI_ResponsibleParty'.isEmpty() || el.'*'['@gco:isoType'].text() == 'gmd:CI_ResponsibleParty'
    }

    def isBBox = { el ->
        el.name() == 'gmd:EX_GeographicBoundingBox'
    }
    def isRoot = { el ->
        el.parent() is el
    }

    def isSkippedContainer = { el ->
        skipContainers.any{it == el.name()}
}

}
