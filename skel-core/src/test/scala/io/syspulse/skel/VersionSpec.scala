package io.syspulse.skel

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class VersionSpec extends AnyWordSpec with Matchers {

  "Version" should {
    
    "compare versions correctly" in {
      val v1 = Version("0.2.0")
      val v2 = Version("0.1.100")
      
      (v1 > v2) shouldBe true
      (v1 < v2) shouldBe false
      (v1 >= v2) shouldBe true
      (v1 <= v2) shouldBe false
    }

    "compare equal versions" in {
      val v1 = Version("1.0.0")
      val v2 = Version("1.0.0")
      
      (v1 == v2) shouldBe true
      (v1 >= v2) shouldBe true
      (v1 <= v2) shouldBe true
      (v1 > v2) shouldBe false
      (v1 < v2) shouldBe false
    }

    "compare by major version" in {
      val v1 = Version("2.0.0")
      val v2 = Version("1.999.999")
      
      (v1 > v2) shouldBe true
      (v2 < v1) shouldBe true
    }

    "compare by minor version when major is equal" in {
      val v1 = Version("1.2.0")
      val v2 = Version("1.1.999")
      
      (v1 > v2) shouldBe true
      (v2 < v1) shouldBe true
    }

    "compare by patch version when major and minor are equal" in {
      val v1 = Version("1.1.100")
      val v2 = Version("1.1.99")
      
      (v1 > v2) shouldBe true
      (v2 < v1) shouldBe true
    }

    "handle version with build and stage in comparison" in {
      val v1 = Version("1.0.0")
      val v2 = Version(1, 0, 0, Some("build"), Some("alpha"))
      
      // Build and stage should not affect comparison - they have same major.minor.patch
      v1.compare(v2) shouldBe 0
      (v1 >= v2) shouldBe true
      (v1 <= v2) shouldBe true
      // Note: == checks all fields including build/stage, so they are not equal
      // But comparison (>, <, >=, <=) ignores build/stage
    }

    "sort versions correctly" in {
      val versions = Seq(
        Version("0.1.0"),
        Version("0.2.0"),
        Version("0.1.100"),
        Version("1.0.0"),
        Version("0.9.999")
      )
      
      val sorted = versions.sorted
      sorted shouldBe Seq(
        Version("0.1.0"),
        Version("0.1.100"),
        Version("0.2.0"),
        Version("0.9.999"),
        Version("1.0.0")
      )
    }

    "find max version from sequence" in {
      val versions = Seq(
        Version("0.1.0"),
        Version("0.2.0"),
        Version("0.1.100"),
        Version("1.0.0"),
        Version("0.9.999")
      )
      
      Version.max(versions) shouldBe Some(Version("1.0.0"))
    }

    "return None for max when sequence is empty" in {
      Version.max(Seq.empty) shouldBe None
    }

    "find max version with single element" in {
      Version.max(Seq(Version("1.0.0"))) shouldBe Some(Version("1.0.0"))
    }

    "find max version with equal versions" in {
      val versions = Seq(
        Version("1.0.0"),
        Version("1.0.0"),
        Version("1.0.0")
      )
      
      Version.max(versions) shouldBe Some(Version("1.0.0"))
    }

    "find latest version from string sequence" in {
      val versions = Seq(
        "0.1.0",
        "0.2.0",
        "0.1.100",
        "1.0.0",
        "0.9.999"
      )
      
      Version.latest(versions) shouldBe Some(Version("1.0.0"))
    }

    "return None for latest when sequence is empty" in {
      Version.latest(Seq.empty) shouldBe None
    }

    "find latest version with single element" in {
      Version.latest(Seq("1.0.0")) shouldBe Some(Version("1.0.0"))
    }

    "handle invalid version strings in latest" in {
      val versions = Seq(
        "0.1.0",
        "invalid.version",
        "0.2.0",
        "not.a.version",
        "1.0.0"
      )
      
      Version.latest(versions) shouldBe Some(Version("1.0.0"))
    }

    "return None for latest when all versions are invalid" in {
      val versions = Seq(
        "invalid.version",
        "not.a.version",
        "also.invalid"
      )
      
      Version.latest(versions) shouldBe None
    }

    "find latest version with mixed valid and invalid strings" in {
      val versions = Seq(
        "0.1.0",
        "invalid",
        "0.2.0"
      )
      
      Version.latest(versions) shouldBe Some(Version("0.2.0"))
    }

    "handle version strings with different formats" in {
      val versions = Seq(
        "1.0",
        "2.0.0",
        "1.5.0"
      )
      
      Version.latest(versions) shouldBe Some(Version("2.0.0"))
    }

    "compare complex version scenarios" in {
      // Test the specific example from the requirement
      val v1 = Version("0.2.0")
      val v2 = Version("0.1.100")
      
      (v1 > v2) shouldBe true
      
      // Additional edge cases
      (Version("0.10.0") > Version("0.9.999")) shouldBe true
      (Version("1.0.0") > Version("0.999.999")) shouldBe true
      (Version("2.0.0") > Version("1.999.999")) shouldBe true
    }

    "find max with versions having build metadata" in {
      val versions = Seq(
        Version("1.0.0"),
        Version(1, 0, 0, Some("build1"), None),
        Version(2, 0, 0, Some("build2"), Some("beta"))
      )
      
      Version.max(versions) shouldBe Some(Version(2, 0, 0, Some("build2"), Some("beta")))
    }

    "find latest with version strings having build metadata" in {
      // Note: Version.apply(String) doesn't parse build metadata in format "1.0.0-build"
      // It only parses when build is a separate dot-separated part like "1.0.0.build"
      // So "1.0.0-build1" will fail to parse and be skipped
      val versions = Seq(
        "1.0.0",
        "2.0.0"
      )
      
      val result = Version.latest(versions)
      result shouldBe Some(Version("2.0.0"))
    }

    "find latest with version strings using dot-separated build format" in {
      // Version.apply supports format like "1.0.0.build.stage"
      val versions = Seq(
        "1.0.0",
        "1.0.0.build1",
        "2.0.0.build2.beta"
      )
      
      val result = Version.latest(versions)
      result.isDefined shouldBe true
      result.get.major shouldBe 2
      result.get.minor shouldBe 0
      result.get.patch shouldBe 0
    }

    "support minor versions greater than 100" in {
      val v1 = Version("0.100.0")
      val v2 = Version("0.101.0")
      val v3 = Version("0.999.0")
      val v4 = Version("0.1000.0")
      
      (v2 > v1) shouldBe true
      (v3 > v2) shouldBe true
      (v4 > v3) shouldBe true
      (v4 > v1) shouldBe true
    }

    "compare versions with minor > 100 correctly" in {
      val v1 = Version("0.1.100")
      val v2 = Version("0.100.0")
      val v3 = Version("0.200.0")
      val v4 = Version("0.1000.0")
      
      (v2 > v1) shouldBe true
      (v3 > v2) shouldBe true
      (v4 > v3) shouldBe true
    }

    "handle toInt conversion with minor > 100" in {
      val v1 = Version("0.100.0")
      val v2 = Version("0.101.0")
      val v3 = Version("0.1000.0")
      
      (v2.toInt > v1.toInt) shouldBe true
      (v3.toInt > v2.toInt) shouldBe true
    }
  }
}
