package tv.mta.flutter_playout;

public class MediaItem {
    public String title;
    public String subtitle;
    public String url;
    public String author;
    public String cover;

    public MediaItem(String title, String subtitle, String url, String author, String cover) {
        this.title = title;
        this.subtitle = subtitle;
        this.url = url;
        this.author = author;
        this.cover = cover;
    }
}
