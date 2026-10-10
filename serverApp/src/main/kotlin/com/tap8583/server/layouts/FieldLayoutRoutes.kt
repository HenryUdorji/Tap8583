package com.tap8583.server.layouts

import com.tap8583.server.accounts.AccessControl
import com.tap8583.server.models.ErrorDto
import com.tap8583.server.models.FieldLayoutDto
import com.tap8583.server.models.FieldPreviewRequest
import com.tap8583.server.models.FieldSampleDto
import com.tap8583.server.storage.Storage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val MAX_PREVIEW_LENGTH = 4096

/** Any member can describe the workspace's private fields; everyone in it sees the result. */
fun Route.fieldLayoutRoutes(
    access: AccessControl,
    layouts: FieldLayoutStore,
    storage: Storage,
    onChange: suspend (workspaceId: String) -> Unit
) {
    fun ApplicationCall.fieldNumber(): Int? = parameters["field"]?.toIntOrNull()

    get("/api/v1/workspaces/{id}/fields") {
        val user = access.requireUser(call) ?: return@get
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@get
        call.respond(layouts.layouts(id.toString()).values.sortedBy { it.field })
    }

    put("/api/v1/workspaces/{id}/fields/{field}") {
        val user = access.requireUser(call) ?: return@put
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@put
        val body = runCatching { call.receive<FieldLayoutDto>() }.getOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid field layout"))
        val layout = FieldLayouts.validate(body.copy(field = call.fieldNumber() ?: body.field)).getOrElse {
            return@put call.respond(HttpStatusCode.BadRequest, ErrorDto(it.message ?: "Invalid field layout"))
        }
        layouts.save(id, layout)
        onChange(id.toString())
        call.respond(layout)
    }

    delete("/api/v1/workspaces/{id}/fields/{field}") {
        val user = access.requireUser(call) ?: return@delete
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@delete
        val field = call.fieldNumber()
        if (field == null || !layouts.delete(id, field)) {
            return@delete call.respond(HttpStatusCode.NotFound, ErrorDto("No layout for that field"))
        }
        onChange(id.toString())
        call.respond(HttpStatusCode.NoContent)
    }

    // Try a layout on a value before saving it.
    post("/api/v1/workspaces/{id}/fields/preview") {
        val user = access.requireUser(call) ?: return@post
        access.requireRole(call, user, call.parameters["id"]) ?: return@post
        val body = runCatching { call.receive<FieldPreviewRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid preview request"))
        val layout = FieldLayouts.validate(body.layout).getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorDto(it.message ?: "Invalid field layout"))
        }
        call.respond(FieldLayouts.explain(layout, body.raw.take(MAX_PREVIEW_LENGTH)))
    }

    // The latest value this workspace has seen for a field, to try layouts on.
    get("/api/v1/workspaces/{id}/fields/{field}/sample") {
        val user = access.requireUser(call) ?: return@get
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@get
        val field = call.fieldNumber() ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid field"))
        val raw = storage.recentTransactions(id.toString(), null, 200).asReversed().firstNotNullOfOrNull { tx ->
            listOfNotNull(tx.request, tx.response).firstNotNullOfOrNull { packet ->
                packet.fields.firstOrNull { it.number == field }?.rawValue
            }
        }
        call.respond(FieldSampleDto(raw))
    }
}
