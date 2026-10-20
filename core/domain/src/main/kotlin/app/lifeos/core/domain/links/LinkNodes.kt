package app.lifeos.core.domain.links

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.domain.ports.LinkRepository
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.NodeLink

sealed interface LinkResult {
    data class Linked(val link: NodeLink) : LinkResult
    data object SelfLink : LinkResult
    data object AlreadyLinked : LinkResult
    data class MissingNode(val id: NodeId) : LinkResult
}

/**
 * Verknüpft zwei beliebige Nodes (ADR-001). Die verknüpften Objekte bleiben unverändert –
 * insbesondere entsteht keine zweite Buchung.
 */
class LinkNodes(
    private val links: LinkRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
) {
    suspend operator fun invoke(from: NodeId, to: NodeId, relation: LinkRelation): LinkResult {
        if (from == to) return LinkResult.SelfLink
        if (links.node(from) == null) return LinkResult.MissingNode(from)
        if (links.node(to) == null) return LinkResult.MissingNode(to)
        if (links.linkExists(from, to, relation)) return LinkResult.AlreadyLinked
        val link = NodeLink(ids.newId(), from, to, relation)
        links.link(link, clock.now())
        return LinkResult.Linked(link)
    }
}
