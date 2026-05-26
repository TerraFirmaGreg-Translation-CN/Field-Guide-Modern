package team.terrafirmgreg.fieldguide.data.patchouli.page;

import lombok.Data;

import java.util.List;

@Data
public class PageRelations extends IPageWithText {

    private List<String> entries;

    private String title;
}
