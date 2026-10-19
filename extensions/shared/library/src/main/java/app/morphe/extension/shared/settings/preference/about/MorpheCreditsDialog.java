/*
 * Vista Project - Sanket Kurve
 * https://github.com/sanket200511/Project-YT
 */

package app.morphe.extension.shared.settings.preference.about;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class MorpheCreditsDialog extends AboutWebViewDialog {

    static final MorpheAboutPreference.WebLink ABOUT_LICENSE = new MorpheAboutPreference.WebLink(
            "Licenses",
            "Open source licenses",
            "https://github.com/sanket200511/Project-YT/blob/main/LICENSE"
    );

    private static final List<MorpheAboutPreference.WebLink> WORKS_LINKS_CURRENT = List.of(
            new MorpheAboutPreference.WebLink("Sanket Kurve",
                    "Lead Developer & Maintainer",
                    "https://linktr.ee/sanketkurve"
            ),
            new MorpheAboutPreference.WebLink("Vista Project",
                    "Official GitHub Repository",
                    "https://github.com/sanket200511/Project-YT"
            )
    );

    private static List<MorpheAboutPreference.WebLink> getWorksLinksPrior() {
        return List.of();
    }

    private static String createDialogHtml() {
        StringBuilder html = new StringBuilder(AboutDialogStyle.documentStart());

        html.append("<div class=\"dialog-header\"><div class=\"dialog-title\">")
                .append(str("morphe_settings_about_links_credits"))
                .append("</div></div>");

        appendContributors(html, "Developer", WORKS_LINKS_CURRENT);
        appendContributors(html, "Legal", List.of(ABOUT_LICENSE));

        html.append(AboutDialogStyle.DOCUMENT_END);

        return html.toString();
    }

    private static void appendContributors(StringBuilder html, String title,
                                           List<MorpheAboutPreference.WebLink> links) {
        html.append("<div class=\"section\">")
                .append(AboutDialogStyle.sectionTitle(title))
                .append("<div class=\"settings-group\">");

        for (MorpheAboutPreference.WebLink link : links) {
            String initial = link.name.substring(0, 1).toUpperCase(Locale.getDefault());
            html.append("<a href=\"").append(link.url).append("\" class=\"settings-item\">")
                    .append("<span class=\"avatar\">").append(initial).append("</span>")
                    .append("<div class=\"item-text\"><div class=\"item-title\">")
                    .append(link.name)
                    .append("</div>");
            if (link.subText != null) {
                html.append("<div class=\"item-subtitle\">").append(link.subText).append("</div>");
            }
            html.append("</div>")
                    .append(AboutDialogStyle.chevron())
                    .append("</a>");
        }

        html.append("</div></div>");
    }

    MorpheCreditsDialog(Context context) {
        super(context, createDialogHtml());
    }
}
