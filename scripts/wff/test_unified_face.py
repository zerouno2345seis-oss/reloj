import os
import re
import unittest
import xml.etree.ElementTree as ET

import make_unified_face as uni


class UnifiedFaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        uni.main()
        path = os.path.join(uni.kit.ROOT, uni.MODULE, "src/main/res/raw/watchface.xml")
        cls.xml = open(path, encoding="utf-8").read()
        cls.root = ET.fromstring(cls.xml.split("\n", 1)[1])

    def test_at_most_eight_slots_all_at_the_top_of_the_scene(self):
        scene = self.root.find("Scene")
        top = scene.findall("ComplicationSlot")
        self.assertLessEqual(len(top), 8)
        self.assertEqual(len(top), len(self.root.findall(".//ComplicationSlot")))

    def test_no_slot_inside_a_list_option(self):
        for opt in self.root.iter("ListOption"):
            self.assertIsNone(opt.find(".//ComplicationSlot"))

    def test_every_design_is_an_option_with_a_name_and_picture(self):
        conf = self.root.find("UserConfigurations/ListConfiguration")
        self.assertEqual([o.get("id") for o in conf.findall("ListOption")], uni.DESIGNS)
        for o in conf.findall("ListOption"):
            self.assertTrue(o.get("displayName") and o.get("icon"))

    def test_no_prayer_window_source_left(self):
        self.assertNotIn("PrayerWindowComplicationService", self.xml)


if __name__ == "__main__":
    unittest.main()
