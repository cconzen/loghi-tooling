package nl.knaw.huc.di.images.pagexmlutils;

import com.google.common.base.Strings;
import nl.knaw.huc.di.images.layoutds.models.Page.TextEquiv;
import nl.knaw.huc.di.images.layoutds.models.Page.TextLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GroundTruthTextLineFormatter {
    private static final Logger LOG = LoggerFactory.getLogger(GroundTruthTextLineFormatter.class);
    public static String getFormattedTextLineStringRepresentation(TextLine textLine, boolean includeTextStyles) {
        final TextEquiv textEquiv = textLine.getTextEquiv();
        String text = null;
        if (textEquiv != null) {
            text = textEquiv.getUnicode();

            if (Strings.isNullOrEmpty(text)) {
                text = textEquiv.getPlainText();
            }
        }

        if (text == null && textLine.getWords() != null && !textLine.getWords().isEmpty()) {
            text = textLine.getWords().stream()
                    .filter(word -> word.getTextEquiv() != null)
                    .map(word -> word.getTextEquiv().getUnicode() != null ? word.getTextEquiv().getUnicode() : word.getTextEquiv().getPlainText())
                    .collect(Collectors.joining(" "));
        }
        String result = format(text, textLine.getCustom(), includeTextStyles);
        return result;
    }

    private static String format(String text, String custom, boolean includeTextStyles) {
        if (Strings.isNullOrEmpty(text) || custom == null || !custom.contains("textStyle") || !includeTextStyles) {
            return text;
        }

        final StyledString styledString = StyledString.fromString(text);
        for (String customPart : custom.split("}")) {
            if (customPart.contains("textStyle")) {
                int offSet = 0;
                int length = 0;
                List<String> styles = new ArrayList<>();
                final String textStyleContents = customPart.substring(customPart.indexOf("textStyle {") + 11);
                final String[] style = textStyleContents.split(";");
                for (String element : style) {
                    final String[] nameValue = element.split(":");
                    final String trimmedName = nameValue[0].trim();
                    switch (trimmedName) {
                        case "offset":
                            offSet = Integer.parseInt(nameValue[1]);
                            break;
                        case "length":
                            length = Integer.parseInt(nameValue[1]);
                            break;
                        default:
                            if (StyledString.isAllowedStyle(trimmedName)) {
                                styles.add(trimmedName);
                            }
                    }
                }
                // if data clearly is incorrect: continue
                if (offSet + length > text.length()){
                    continue;
                }
                try {
                    styledString.applyStyles(offSet, length, styles);
                } catch (IndexOutOfBoundsException e) {
                    LOG.error("Error applying styles to text: " + text + " with custom: " + custom);
                    throw e;
                }
            }
        }

        return styledString.toString();
    }
}
