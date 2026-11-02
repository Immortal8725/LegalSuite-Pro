package com.legalsuite.blog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {
    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<BlogPost> findByStatusOrderByPublishedAtDesc(String status, Pageable pageable);

    Page<BlogPost> findByStatusAndPracticeAreaOrderByPublishedAtDesc(String status, String practiceArea, Pageable pageable);

    List<BlogPost> findAllByOrderByUpdatedAtDesc();
}
