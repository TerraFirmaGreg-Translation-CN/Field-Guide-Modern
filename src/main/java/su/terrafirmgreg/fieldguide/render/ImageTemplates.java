package su.terrafirmgreg.fieldguide.render;

public class ImageTemplates {
    public static final String IMAGE_SINGLE = 
        "<img class=\"d-block w-200 mx-auto img-fluid\" src=\"../../%s\" alt=\"%s\">";
    
    public static final String IMAGE_KNAPPING = 
        "<div class=\"d-flex align-items-center justify-content-center\">" +
        "    <div class=\"knapping-recipe\">" +
        "        <img class=\"knapping-recipe-img\" src=\"../../_images/knapping.png\">" +
        "        <div class=\"knapping-recipe-overlay\">" +
        "            <img class=\"knapping-recipe-img\" src=\"../../%s\" alt=\"%s\">" +
        "        </div>" +
        "    </div>" +
        "</div>";
    
    public static final String IMAGE_MULTIPLE_PART = 
        "<div class=\"carousel-item %s\">" +
        "    <img class=\"d-block w-200 mx-auto img-fluid\" src=\"../../%s\" alt=\"%s\">" +
        "</div>";
    
    public static final String IMAGE_MULTIPLE_SEQ = 
        "<button type=\"button\" data-bs-target=\"#%s\" data-bs-slide-to=\"%s\" aria-label=\"Slide %s\"></button>";
    
    public static final String IMAGE_MULTIPLE = 
        "<div id=\"{0}\" class=\"carousel slide\" data-bs-ride=\"carousel\">" +
        "    <div class=\"carousel-indicators\">" +
        "        <button type=\"button\" data-bs-target=\"#{0}\" data-bs-slide-to=\"0\" class=\"active\" aria-current=\"true\" aria-label=\"Slide 1\"></button>" +
        "        {1}" +
        "    </div>" +
        "    <div class=\"carousel-inner\">" +
        "        {2}" +
        "    </div>" +
        "    <button type=\"button\" class=\"carousel-control-prev\" data-bs-target=\"#{0}\" data-bs-slide=\"prev\">" +
        "        <span class=\"carousel-control-prev-icon\" aria-hidden=\"true\"></span>" +
        "        <span class=\"visually-hidden\">Previous</span>" +
        "    </button>" +
        "    <button type=\"button\" class=\"carousel-control-next\" data-bs-target=\"#{0}\" data-bs-slide=\"next\">" +
        "        <span class=\"carousel-control-next-icon\" aria-hidden=\"true\"></span>" +
        "        <span class=\"visually-hidden\">Next</span>" +
        "    </button>" +
        "</div>";
}