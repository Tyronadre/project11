package de.tyro.project11.portal;

public enum TravelKind {
    LEAVE("aab", "AaB", "Antrag auf Beurlaubung", "B"),
    REPORT("eer", "EeR", "Einreichung eines Reiseberichtes", "C");

    public final String route;
    public final String code;
    public final String title;
    public final String department;
    TravelKind(String route, String code, String title, String department) {
        this.route = route;
        this.code = code;
        this.title = title;
        this.department = department;
    }
    public String getRoute() { return route; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDepartment() { return department; }
}
