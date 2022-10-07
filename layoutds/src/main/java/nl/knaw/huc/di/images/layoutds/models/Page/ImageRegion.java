package nl.knaw.huc.di.images.layoutds.models.Page;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class ImageRegion extends Region {

    @JacksonXmlProperty(isAttribute = true, localName = "colourDepth")
    private String colourDepth;
    @JacksonXmlProperty(isAttribute = true, localName = "bgColour")
    private String bgColour;
    @JacksonXmlProperty(isAttribute = true, localName = "embText")
    private Boolean embText;

    public void setColourDepth(String colourDepth) {
        this.colourDepth = colourDepth;
    }

    public String getColourDepth() {
        return colourDepth;
    }

    public void setBgColour(String bgColour) {
        this.bgColour = bgColour;
    }

    public String getBgColour() {
        return bgColour;
    }

    public void setEmbText(Boolean embText) {
        this.embText = embText;
    }

    public Boolean getEmbText() {
        return embText;
    }
}
