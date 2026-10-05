# Class diagrams

Package-level views of the Spring Boot modular monolith. UI pages in Next.js map 1:1 onto these services via `/api/v1`.

## Auth, tenant, modules

```mermaid
classDiagram
    class TenantContext {
        +UUID tenantId
        +UUID userId
        +String role
        +requireTenant() UUID
    }
    class AuthService {
        +login()
        +register()
        +refresh()
        +portalLogin()
    }
    class TenantService {
        +plans()
        +modules()
        +toggleModule()
        +updateFirm()
        +invite()
    }
    class JwtService {
        +issue(user, tenant)
        +portalToken(client, tenant)
        +parse(token)
    }
    class Tenant
    class AppUser
    class Plan
    class AppModule
    class TenantModule
    AuthService --> JwtService
    AuthService --> Tenant
    AuthService --> AppUser
    TenantService --> Tenant
    TenantService --> AppModule
    TenantService --> TenantModule
    JwtAuthFilter --> JwtService
    JwtAuthFilter --> TenantContext
```

## Practice

```mermaid
classDiagram
    class PracticeService {
        +listClients()
        +saveCase()
        +upload()
        +eventsBetween()
        +saveTask()
    }
    class Client
    class LegalCase
    class Contact
    class DocumentFile
    class CalendarEvent
    class TaskItem
    class Note
    PracticeService --> Client
    PracticeService --> LegalCase
    PracticeService --> DocumentFile
    PracticeService --> CalendarEvent
    PracticeService --> TaskItem
    LegalCase --> Client : clientId
    Note --> LegalCase : caseId
```

## Finance

```mermaid
classDiagram
    class FinanceService {
        +logTime()
        +startTimer()
        +generateInvoice()
        +trustMove()
        +addExpense()
    }
    class TimeEntry
    class Invoice
    class TrustAccount
    class TrustTransaction
    class Expense
    FinanceService --> TimeEntry
    FinanceService --> Invoice
    FinanceService --> TrustAccount
    TrustTransaction --> TrustAccount
```

## Voice and messaging

```mermaid
classDiagram
    class VoiceService {
        +initiate()
        +answer()
        +end()
        +placePstn()
        +bridgeTwiml()
        +iceServers()
        +pushSignal()
    }
    class FirmNumberService {
        +search()
        +buy()
        +startVerification()
        +requireForDial()
    }
    class TwilioGateway {
        +createCall()
        +sendMessage()
    }
    class OutboundMessageService {
        +sendSms()
        +sendWhatsapp()
        +sendEmail()
    }
    class OutboundMessage
    class MailGateway
    class CommsService {
        +conversations()
        +sendMessage()
        +publicLanding()
        +intake()
        +conflictCheck()
    }
    class SignalingHandler
    class CallRecord
    class Conversation
    class ChatMessage
    class Lead
    class ConflictCheck
    VoiceService --> CallRecord
    VoiceService --> FirmNumberService
    FirmNumberService --> TwilioGateway
    OutboundMessageService --> TwilioGateway
    OutboundMessageService --> MailGateway
    OutboundMessageService --> OutboundMessage
    SignalingHandler --> VoiceService
    CommsService --> Conversation
    CommsService --> Lead
    Conversation --> ChatMessage
```

## Draft help, templates, e-sign, integrations

```mermaid
classDiagram
    class MergeEngine {
        +merge(body, values)$ String
    }
    class HeuristicAi {
        +screenIntake()$
        +summarize()$
        +draftEmail()$
    }
    class TemplateService {
        +save()
        +merge()
    }
    class SignatureService {
        +create()
        +sign()
        +publicView()
    }
    class AiService {
        +chat()
        +summarize()
        +draftEmail()
        +screenIntake()
    }
    class IntegrationService {
        +list()
        +setConnected()
    }
    class AuditService {
        +record()
        +list()
    }
    class DocumentTemplate
    class SignatureRequest
    class ConnectedIntegration
    class AuditLog
    TemplateService --> MergeEngine
    TemplateService --> DocumentTemplate
    AiService --> HeuristicAi
    SignatureService --> SignatureRequest
    IntegrationService --> ConnectedIntegration
    TemplateService --> AuditService
    AiService --> AuditService
```

## Frontend (Next.js)

```mermaid
classDiagram
    class AuthProvider
    class AppShell
    class TimerProvider
    class CallProvider
    AuthProvider --> AppShell
    TimerProvider --> AppShell
    CallProvider --> AppShell
    AppShell --> DashboardPage
    AppShell --> CasesPage
    AppShell --> BillingPage
    AppShell --> VoicePage
    AppShell --> AiPage
    AppShell --> TemplatesPage
    AppShell --> EsignPage
```
