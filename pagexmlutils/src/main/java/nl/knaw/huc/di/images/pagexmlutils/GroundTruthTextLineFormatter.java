package nl.knaw.huc.di.images.pagexmlutils;

import nl.knaw.huc.di.images.layoutds.models.Page.TextEquiv;
import nl.knaw.huc.di.images.layoutds.models.Page.TextLine;
import org.apache.logging.log4j.util.Strings;

import java.util.stream.Collectors;

public class GroundTruthTextLineFormatter {
    public static String SUPERSCRIPTCHAR = "␆";
    public static String UNDERLINECHAR = "␅";
    public static String getFormattedTextLineStringRepresentation(TextLine textLine, boolean includeTextStyles) {
        final TextEquiv textEquiv = textLine.getTextEquiv();
        String text = null;
        if (textEquiv != null) {
            text = textEquiv.getUnicode();

            if (Strings.isBlank(text)) {
                text = textEquiv.getPlainText();
            }
        }

        if (text == null && textLine.getWords() != null && !textLine.getWords().isEmpty()) {
            text = textLine.getWords().stream()
                    .filter(word -> word.getTextEquiv() != null)
                    .map(word -> word.getTextEquiv().getUnicode() != null ? word.getTextEquiv().getUnicode() : word.getTextEquiv().getPlainText())
                    .collect(Collectors.joining(" "));
        }
        text = format(text, textLine.getCustom(), includeTextStyles);
        return text;
    }

    private static String format(String text, String custom, boolean includeTextStyles) {
        if (text == null || custom == null || !custom.contains("textStyle") || !includeTextStyles) {
            return text;
        }

        final String textStyle = custom.substring(custom.indexOf("textStyle"));
        final String textStyleContents = textStyle.substring(textStyle.indexOf("{") + 1, textStyle.indexOf("}"));
        final String[] style = textStyleContents.split(";");

        boolean superScript = false;
        boolean underlined = false;
        int offSet = 0;
        int length = 0;
        for (String element : style) {
            final String[] nameValue = element.split(":");
            switch (nameValue[0].trim()) {
                case "superscript":
                    superScript = Boolean.parseBoolean(nameValue[1]);
                    break;
                case "underlined":
                    underlined = Boolean.parseBoolean(nameValue[1]);
                    break;
                case "offset":
                    offSet = Integer.parseInt(nameValue[1]);
                    break;
                case "length":
                    length = Integer.parseInt(nameValue[1]);
                    break;
            }

            if (superScript) {
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(text.substring(0, offSet - 1));
                final String superScriptText = text.substring(offSet - 1, offSet + length - 1);
                for (int i = 0; i < superScriptText.length(); i++) {
                    stringBuilder.append(SUPERSCRIPTCHAR).append(superScriptText.charAt(i));
                }
                stringBuilder.append(text.substring(offSet + length - 1));
                text = stringBuilder.toString();
            }
            if (underlined) {
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(text.substring(0, offSet - 1));
                final String underlinedText = text.substring(offSet - 1, offSet + length - 1);
                for (int i = 0; i < underlinedText.length(); i++) {
                    stringBuilder.append(UNDERLINECHAR).append(underlinedText.charAt(i));
                }
                stringBuilder.append(text.substring(offSet + length - 1));
                text = stringBuilder.toString();
            }

        }


        return text;
    }
}
