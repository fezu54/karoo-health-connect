package org.happycode.karoo.health_connect.adapters.sram

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

class PkceGeneratorTest : FreeSpec({

    "should generate valid URL-safe base64 code verifier" {
        //when
        val verifier = PkceGenerator.generateCodeVerifier()

        //then
        verifier.length shouldBe 43
        verifier shouldMatch Regex("^[A-Za-z0-9_-]+$")
    }

    "should generate distinct code verifiers on consecutive invocations" {
        //when
        val verifier1 = PkceGenerator.generateCodeVerifier()
        val verifier2 = PkceGenerator.generateCodeVerifier()

        //then
        verifier1 shouldNotBe verifier2
    }

    "should generate deterministic SHA-256 code challenge from known verifier" {
        //given (RFC 7636 Appendix B test vector)
        val testVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

        //when
        val challenge = PkceGenerator.generateCodeChallenge(testVerifier)

        //then
        challenge shouldBe expectedChallenge
    }
})
