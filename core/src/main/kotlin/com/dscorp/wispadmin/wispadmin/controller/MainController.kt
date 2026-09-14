package com.dscorp.wispadmin.wispadmin.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")

class MainController {

@GetMapping
    fun home(): String? {
        return "Hello World!"
    }
}
