package com.dscorp.wispadmin.wispadmin.service.whatsapp

class CrmConversationNotFoundException(message: String) : RuntimeException(message)

class CrmConversationConflictException(message: String) : RuntimeException(message)

class CrmConversationForbiddenException(message: String) : RuntimeException(message)

class CrmConversationValidationException(message: String) : RuntimeException(message)
