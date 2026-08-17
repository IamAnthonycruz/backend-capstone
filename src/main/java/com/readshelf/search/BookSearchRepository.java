package com.readshelf.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data repository over the "books" index. Same programming model as the JPA repositories,
 * different store — save/findById/deleteById all speak to Elasticsearch instead of Postgres.
 *
 * The id type is String, not UUID, because that's what BookDocument.id is: Elasticsearch's `_id`
 * is always a string. Use BookDocument.documentId(uuid) rather than calling toString() at each
 * call site.
 *
 * Note there is no `save` vs `update` distinction here — indexing a document whose id already
 * exists REPLACES it. That's what makes reindexing safe to run twice on the same event.
 */
public interface BookSearchRepository extends ElasticsearchRepository<BookDocument, String> {
}
