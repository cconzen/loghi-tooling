package nl.knaw.huc.di.images.minions;


import nl.knaw.huc.di.images.layoutds.models.Page.TextEquiv;
import nl.knaw.huc.di.images.layoutds.models.Page.TextLine;
import nl.knaw.huc.di.images.layoutds.models.Page.TextLineCustom;
import nl.knaw.huc.di.images.pagexmlutils.GroundTruthTextLineFormatter;
import org.junit.jupiter.api.Test;

import static nl.knaw.huc.di.images.pagexmlutils.StyledString.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class MinionLoghiHTRMergePageXMLTest {

    @Test
    public void checkDetectSuperscript() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom(SUPERSCRIPTCHAR + "t" + SUPERSCRIPTCHAR + "e");

        assertThat(result.getTextStyles(), contains(allOf(
                containsString("superscript:true"),
                containsString("offset:0"),
                containsString("length:2")
        )));
    }

    @Test
    public void checkDetectSuperscriptWithOffset() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("t" + SUPERSCRIPTCHAR + "e");
        String custom = result.getTextStyles().get(0);
        final String textStyle = custom.substring(custom.indexOf("textStyle"));

        assertThat(textStyle, allOf(
                containsString("superscript:true"),
                containsString("offset:1"),
                containsString("length:1")
        ));
    }

    @Test
    public void checkDetectMultipleSuperscriptParts() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + SUPERSCRIPTCHAR + "i" + SUPERSCRIPTCHAR + "s" + " een " + SUPERSCRIPTCHAR + "t" + SUPERSCRIPTCHAR + "e" + SUPERSCRIPTCHAR + "s" + SUPERSCRIPTCHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("superscript:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("superscript:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectUnderlined() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("t" + UNDERLINECHAR + "e");

        assertThat(result.getTextStyles(), contains(allOf(
                containsString("underlined:true"),
                containsString("offset:1"),
                containsString("length:1")
        )));
    }

    @Test
    public void checkDetectMultipleUnderlineParts() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + UNDERLINECHAR + "i" + UNDERLINECHAR + "s" + " een " + UNDERLINECHAR + "t" + UNDERLINECHAR + "e" + UNDERLINECHAR + "s" + UNDERLINECHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("underlined:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("underlined:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleSubscriptParts() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + SUBSCRIPTCHAR + "i" + SUBSCRIPTCHAR + "s" + " een " + SUBSCRIPTCHAR + "t" + SUBSCRIPTCHAR + "e" + SUBSCRIPTCHAR + "s" + SUBSCRIPTCHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("subscript:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("subscript:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStrikeThroughParts() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + STRIKETHROUGHCHAR + "i" + STRIKETHROUGHCHAR + "s" + " een " + STRIKETHROUGHCHAR + "t" + STRIKETHROUGHCHAR + "e" + STRIKETHROUGHCHAR + "s" + STRIKETHROUGHCHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("strikethrough:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("strikethrough:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStyles() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom(SUPERSCRIPTCHAR + "Dit " + STRIKETHROUGHCHAR + "i" + STRIKETHROUGHCHAR + "s" + " een " + UNDERLINECHAR + "t" + UNDERLINECHAR + "e" + UNDERLINECHAR + "s" + UNDERLINECHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("superscript:true"),
                        containsString("offset:0"),
                        containsString("length:1")
                ),
                allOf(containsString("strikethrough:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("underlined:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStylesConnecting() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom(SUBSCRIPTCHAR + "D" + SUBSCRIPTCHAR + "i" + SUBSCRIPTCHAR + "t " + STRIKETHROUGHCHAR + "i" + STRIKETHROUGHCHAR + "s" + " een " + UNDERLINECHAR + "t" + UNDERLINECHAR + "e" + UNDERLINECHAR + "s" + UNDERLINECHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("subscript:true"),
                        containsString("offset:0"),
                        containsString("length:3")
                ),
                allOf(containsString("strikethrough:true"),
                        containsString("offset:4"),
                        containsString("length:2")
                ),
                allOf(containsString("underlined:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStylesOnSameCharacteres() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + "is" + " een " + UNDERLINECHAR + STRIKETHROUGHCHAR + "t" + UNDERLINECHAR + STRIKETHROUGHCHAR + "e" + UNDERLINECHAR + STRIKETHROUGHCHAR + "s" + UNDERLINECHAR + STRIKETHROUGHCHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("strikethrough:true"),
                        containsString("underlined:true"),
                        containsString("offset:11"),
                        containsString("length:4")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStylesWithinAStyle() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + "is" + " een " + STRIKETHROUGHCHAR + "t" + UNDERLINECHAR + STRIKETHROUGHCHAR + "e" + UNDERLINECHAR + STRIKETHROUGHCHAR + "s" + STRIKETHROUGHCHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("strikethrough:true"),
                        containsString("offset:11"),
                        containsString("length:1")
                ),
                allOf(containsString("underlined:true"),
                        containsString("strikethrough:true"),
                        containsString("offset:12"),
                        containsString("length:2")
                ),
                allOf(containsString("strikethrough:true"),
                        containsString("offset:14"),
                        containsString("length:1")
                )
        ));
    }

    @Test
    public void checkDetectMultipleStylesPartialOverlap() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit " + "is" + " een " + STRIKETHROUGHCHAR + "t" + UNDERLINECHAR + STRIKETHROUGHCHAR + "e" + UNDERLINECHAR + STRIKETHROUGHCHAR + "s" + UNDERLINECHAR + "t");

        assertThat(result.getTextStyles(), hasItems(
                allOf(containsString("strikethrough:true"),
                        containsString("offset:11"),
                        containsString("length:1")
                ),
                allOf(containsString("underlined:true"),
                        containsString("strikethrough:true"),
                        containsString("offset:12"),
                        containsString("length:2")
                ),
                allOf(containsString("underlined:true"),
                        containsString("offset:14"),
                        containsString("length:1")
                )
        ));
    }

    @Test
    public void checkDetectWithoutStyles() {
        TextLineCustom result = MinionLoghiHTRMergePageXML.getTextLineCustom("Dit is een test");

        assertThat(result.getTextStyles(), is(nullValue()));
    }

    @Test
    public void checkDetectMultipleStylesPartialOverlapFullCircleTest() {
        final TextLine textLine = new TextLine();
        textLine.setTextEquiv(new TextEquiv(null, "Dit is een test"));
        final String custom = "textStyle {offset:7; length:5;strikethrough:true;} textStyle {offset:4; length:5;underlined:true;}";
        textLine.setCustom(custom);
        final String textRepresentation = GroundTruthTextLineFormatter.getFormattedTextLineStringRepresentation(textLine, true);

        final TextLineCustom textLineCustom = MinionLoghiHTRMergePageXML.getTextLineCustom(textRepresentation);

        final TextLine textLine2 = new TextLine();
        textLine.setTextEquiv(new TextEquiv(null, "Dit is een test"));
        textLine2.setCustom(textLineCustom.toString());

        final String textRepresentation2 = GroundTruthTextLineFormatter.getFormattedTextLineStringRepresentation(textLine, true);


        assertThat(textRepresentation, is(textRepresentation2));
    }

}