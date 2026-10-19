# Templates

[Back to README](../README.md)

## Overview

Everything the executor writes is a template: the summary and description of the Jira issue, and the messages the marketplace shows to users. Templates let you put request data (data product, ports, consumer, dates) into the text without writing code. The same engine renders both, with different data available.

Related: [Configuration reference](configuration-reference.md) · [Use cases](use-cases.md) · [Concepts](concepts.md)

## Qute primer

Issue fields and messages are written in [Qute](https://quarkus.io/guides/qute-reference), the template engine of Quarkus.

> **Qute `{...}` in the executor configuration is not the same as Jira smart values `{{...}}` in Automation rules.** Qute is evaluated by the executor on the marketplace request, before the issue exists. Smart values are evaluated by Jira inside an Automation rule. `{request.identifier}` is Qute, `{{issue.key}}` is a smart value.

| You want | Write |
|:--|:--|
| A value | `{request.provider.dataProductFqn}` (single braces; **not** Mustache's `{{...}}`) |
| A condition | `{#if ticket.closedBy} by {ticket.closedBy}{/if}` |
| If / else | `{#if operation == 'MARKETPLACE_SUBSCRIBE'}Grant{#else}Revoke{/if}` |
| A loop | `{#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}` |
| A literal brace | `{|{"a": 1}|}` (unparsed section) or `\{` |

Rules and gotchas:

- **Quote templates in YAML** whenever they contain `{`, `:` or `#`. Use double quotes for templates and single quotes for regexes. Multi-line texts use a block scalar (`|-`).
- **Never write `${...}` in a template**: Quarkus expands it as a configuration expression before the template is parsed.
- **No escaping**: rendered values are plain strings, serialised to JSON by the executor (no HTML escaping, no JSON-injection risk).
- **Missing values render empty**: `{request.properties.costCenter}` on a request without that property renders nothing and never fails. `{#if}` is false for empty and missing values. This relies on `quarkus.qute.strict-rendering: false` and `quarkus.qute.property-not-found-strategy: noop`, preconfigured in `application.yml`. Do not change them.
- **External templates**: a value starting with `file:` is loaded from that path, for example `description: "file:/etc/marketplace/description.qute"`. A file that does not exist or does not parse stops the executor at startup.
- Dates are ISO-8601 strings (empty when not set).
- All templates are parsed at startup: an unparsable one stops the executor with a clear message.

## Data model

| Available in | Expression | Content |
|:--|:--|:--|
| issue fields and messages | `request.identifier`, `request.name`, `request.operation` | UUID, name, operation |
| | `request.startDate`, `request.endDate` | ISO-8601, or empty |
| | `request.properties.<key>` | request properties |
| | `request.provider.dataProductFqn`, `request.provider.dataProductPortsFqn` | provider and the list of **all** ports |
| | `request.consumer.type`, `request.consumer.identifier` | consumer |
| | `request.requester.type`, `request.requester.identifier` | requester |
| | `operation` | `MARKETPLACE_SUBSCRIBE` or `MARKETPLACE_UNSUBSCRIBE` |
| messages only | `ticket.key`, `ticket.id`, `ticket.link` | the issue (`PENDING`: the created issue, empty keys with the Automation kind; final: from the event `key`, `id`, link built from the site URL) |
| | `ticket.closedBy` | the Jira actor. **Present only when closing-user inclusion is enabled** |
| | `fields.<name>` | the normalised event fields (final results). With inclusion disabled, the closing-user field is removed from here too |
| | `outcome.cause`, `outcome.status` | `FULFILLED`/`REFUSED`/`FAILED` and the final status |
| | `error.message` | technical error text (`technical-error` only) |

## Issue content

Three profile maps build the issue body:

| Key (under `profiles.<op>`) | Result |
|:--|:--|
| `fields.<name>` | Field name to template. Rendered to a JSON **string** |
| `object-fields.<name>.<entry>` | A field that is a JSON object, one template per entry. Example: `priority: {name: "High"}` |
| `list-fields.<name>` | A field that is a JSON array of strings, one template per item. Example: `labels` |
| `rich-text-fields` | Default `[description]`. With `api-version: 3`, the rendered values of these fields become an ADF document |

```yaml
fields:
  summary: "Grant access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
  description: |-
    Marketplace request: {request.identifier}
    Ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}
object-fields:
  priority:
    name: "High"
list-fields:
  labels: ["marketplace"]
```

Reserved fields cannot be declared; see [Concepts](concepts.md#reserved-fields).

## Messages

The template used for a message is the first available of: the **rule's** `message`, the **profile's** `messages.<kind>`, the **global** `jira.messages.<kind>`. Messages are trimmed and truncated to 4000 characters.

| Message | Used for |
|:--|:--|
| `pending` | `PENDING` after the issue is opened |
| `fulfilled` | Cause `FULFILLED` (`GRANTED` / `REVOKED`) |
| `refused` | Cause `REFUSED` (`DENIED` for subscribe, `ERROR` for unsubscribe) |
| `failed` | Cause `FAILED` (`ERROR`) |
| `technical-error` | `ERROR` when the issue cannot be opened (Jira unreachable, 401/403, requester not found, a correlation edit failure, rate limit exhausted, ...) |

The global defaults (also in `src/main/resources/application.yml` and `JiraConfig`):

```yaml
jira:
  messages:
    pending: "{#if ticket.key}Jira issue {ticket.key} opened{#else}Request submitted to the Jira Automation rule{/if} for {operation} of {request.provider.dataProductFqn} (ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}). Awaiting fulfilment{#if ticket.link}: {ticket.link}{/if}"
    fulfilled: "{outcome.status} through Jira issue {ticket.key} ({ticket.link}){#if ticket.closedBy} by {ticket.closedBy}{/if}: {fields.status}{#if fields.resolution} / {fields.resolution}{/if}.{#if fields.lastComment} Comment: {fields.lastComment}{/if}"
    refused: "Request refused on Jira: issue {ticket.key} ({ticket.link}){#if ticket.closedBy} by {ticket.closedBy}{/if}, {fields.status}{#if fields.resolution} / {fields.resolution}{/if}.{#if fields.lastComment} Reason: {fields.lastComment}{/if}"
    failed: "Jira issue {ticket.key} ({ticket.link}) was closed without a recognised resolution ({fields.status}{#if fields.resolution} / {fields.resolution}{/if}).{#if fields.lastComment} Details: {fields.lastComment}{/if}"
    technical-error: "Technical error while processing {operation} on Jira: {error.message}"
    closing-user:
      include: ${JIRA_MESSAGES_INCLUDE_CLOSING_USER:true}
      field: actor
```
