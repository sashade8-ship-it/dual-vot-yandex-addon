#!/usr/bin/env python3
"""Small regression tests for validation rules that cannot be inferred by a build."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("validate_addon.py")
SPEC = importlib.util.spec_from_file_location("validate_addon", SCRIPT)
assert SPEC and SPEC.loader
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class TimestampValidationTest(unittest.TestCase):
    def test_timezone_free_timestamp_is_accepted(self) -> None:
        self.assertEqual(
            VALIDATOR.parse_timezone_free_timestamp("2026-08-02T12:34:56", "created_at"),
            "2026-08-02T12:34:56",
        )

    def test_timezone_suffix_is_rejected(self) -> None:
        with self.assertRaises(VALIDATOR.ValidationError):
            VALIDATOR.parse_timezone_free_timestamp("2026-08-02T12:34:56Z", "created_at")
        with self.assertRaises(VALIDATOR.ValidationError):
            VALIDATOR.parse_timezone_free_timestamp("2026-08-02T12:34:56+00:00", "created_at")


class AddOnManagerContractValidationTest(unittest.TestCase):
    def test_exact_manager_contract_is_accepted(self) -> None:
        VALIDATOR.validate_add_on_manager_contract(
            {
                "add_on_manager": {
                    "class_descriptor": VALIDATOR.ADD_ON_MANAGER_CLASS_DESCRIPTOR,
                    "required_methods": ["registerAddOns()V"],
                }
            }
        )

    def test_missing_manager_contract_is_rejected(self) -> None:
        with self.assertRaises(VALIDATOR.ValidationError):
            VALIDATOR.validate_add_on_manager_contract({})


if __name__ == "__main__":
    unittest.main()
