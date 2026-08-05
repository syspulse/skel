package io.syspulse.skel.wf.ext

/**
 * Shared test setup for specs that build WorkflowRoutes:
 *   - runs in "GOD" mode so the route authorizers bypass JWT auth (these specs test functionality,
 *     not authorization; the server enforces real auth - see ExplainRoutesSpec for the JWT pattern);
 *   - provides the implicit `Config` required by WorkflowRoutes.
 * `Permissions.isGod` is read once from the GOD system property, so it is set here before any route.
 */
trait WfRouteTest {
  System.setProperty("GOD", "1")
  implicit val config: Config = Config()
}
