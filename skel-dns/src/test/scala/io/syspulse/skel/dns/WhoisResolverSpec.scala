package io.syspulse.skel.dns

// Run with bloop (from repo root): bloop test skel_dns-test

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class WhoisResolverSpec extends AnyWordSpec with Matchers {

  "getDomain" should {
    "extract domain for simple two-part (gTLD)" in {
      WhoisResolver.getDomain("google.com") should === ("google.com")
      WhoisResolver.getDomain("example.org") should === ("example.org")
    }
    "strip subdomain for gTLD" in {
      WhoisResolver.getDomain("www.google.com") should === ("google.com")
      WhoisResolver.getDomain("mail.example.org") should === ("example.org")
      WhoisResolver.getDomain("a.b.example.com") should === ("example.com")
    }
    "extract registrable domain for UK 2LD co.uk" in {
      WhoisResolver.getDomain("example.co.uk") should === ("example.co.uk")
      WhoisResolver.getDomain("www.example.co.uk") should === ("example.co.uk")
      WhoisResolver.getDomain("mail.bbc.co.uk") should === ("bbc.co.uk")
    }
    "extract registrable domain for UK 2LD ac.uk" in {
      WhoisResolver.getDomain("cam.ac.uk") should === ("cam.ac.uk")
      WhoisResolver.getDomain("www.oxford.ac.uk") should === ("oxford.ac.uk")
    }
    "extract registrable domain for UK 2LD org.uk" in {
      WhoisResolver.getDomain("example.org.uk") should === ("example.org.uk")
      WhoisResolver.getDomain("www.example.org.uk") should === ("example.org.uk")
    }
    "extract registrable domain for Japan 2LD co.jp" in {
      WhoisResolver.getDomain("example.co.jp") should === ("example.co.jp")
      WhoisResolver.getDomain("www.example.co.jp") should === ("example.co.jp")
    }
    "extract registrable domain for Japan 2LD ac.jp" in {
      WhoisResolver.getDomain("example.ac.jp") should === ("example.ac.jp")
      WhoisResolver.getDomain("www.tokyo.ac.jp") should === ("tokyo.ac.jp")
    }
    "extract registrable domain for Australia 2LD com.au" in {
      WhoisResolver.getDomain("example.com.au") should === ("example.com.au")
      WhoisResolver.getDomain("www.example.com.au") should === ("example.com.au")
    }
    "extract registrable domain for Australia 2LD edu.au" in {
      WhoisResolver.getDomain("sydney.edu.au") should === ("sydney.edu.au")
    }
    "extract registrable domain for Austria 2LD co.at" in {
      WhoisResolver.getDomain("example.co.at") should === ("example.co.at")
    }
    "extract registrable domain for Ukraine 2LD co.ua" in {
      WhoisResolver.getDomain("example.co.ua") should === ("example.co.ua")
    }
    "extract registrable domain for India 2LD co.in" in {
      WhoisResolver.getDomain("example.co.in") should === ("example.co.in")
    }
    "return single-part as-is" in {
      WhoisResolver.getDomain("localhost") should === ("localhost")
    }
  }
}
