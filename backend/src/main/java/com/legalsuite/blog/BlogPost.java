package com.legalsuite.blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An Owlaw blog post. Body is Markdown; the web app escapes it before rendering. */
@Entity
@Table(name = "blog_posts")
@Getter
@Setter
@NoArgsConstructor
public class BlogPost {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 400)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "cover_ref", length = 500)
    private String coverRef;

    @Column(name = "cover_type", length = 60)
    private String coverType;

    @Column(name = "practice_area", length = 80)
    private String practiceArea;

    @Column(length = 80)
    private String city;

    @Column(name = "author_name", length = 120)
    private String authorName;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "seo_title", length = 200)
    private String seoTitle;

    @Column(name = "seo_description", length = 300)
    private String seoDescription;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
