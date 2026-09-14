import unittest

from ensure_tomcat_compose_env import ensure_tomcat_env_file


MINIMAL_COMPOSE = """services:
  mysql:
    image: mysql
  tomcat:
    image: tomcat
    container_name: tomcat9027
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"
"""


class EnsureTomcatEnvFileTest(unittest.TestCase):
    def test_inserts_env_file_after_restart(self):
        result = ensure_tomcat_env_file(MINIMAL_COMPOSE, "/opt/gigafiber/.env")
        self.assertIn("    env_file:", result)
        self.assertIn("      - /opt/gigafiber/.env", result)
        restart_idx = result.index("restart: unless-stopped")
        env_file_idx = result.index("env_file:")
        self.assertLess(restart_idx, env_file_idx)

    def test_idempotent_when_already_present(self):
        once = ensure_tomcat_env_file(MINIMAL_COMPOSE)
        twice = ensure_tomcat_env_file(once)
        self.assertEqual(once, twice)

    def test_raises_when_tomcat_restart_missing(self):
        broken = """services:
  tomcat:
    image: tomcat
    environment:
      SPRING_PROFILES_ACTIVE: prod
"""
        with self.assertRaises(ValueError):
            ensure_tomcat_env_file(broken)


if __name__ == "__main__":
    unittest.main()
