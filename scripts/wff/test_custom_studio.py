import unittest

import make_custom_face as face


class StudioFaceTest(unittest.TestCase):
    def spec(self):
        return {
            "clock": "analog", "ring": {"thickness": 0},
            "elements": {key: {"show": False} for key in ("date", "pill", "time", "prayer", "edges")},
            "dialTexture": "sunburst", "hourIndex": "baton", "innerBezel": True,
            "pods": {"shape": "circle", "positions": [0, 90, 180, 270], "distance": 110,
                     "radius": 36, "items": [{"content": x} for x in ("moon", "battery", "steps", "heart")]},
        }

    def test_subdials_get_real_data_slots_and_static_moon(self):
        result = face.build(self.spec())
        xml = "".join(result.parts)
        self.assertEqual(xml.count("<ComplicationSlot "), 4)  # three data slots and time tap
        self.assertIn("WATCH_BATTERY", xml)
        self.assertIn("STEP_COUNT", xml)
        self.assertIn("HEART_RATE", xml)
        self.assertIn("podMoon0", xml)
        self.assertIn("dialFinish", xml)

    def test_app_content_gets_its_own_slot_and_unknown_content_draws_empty(self):
        spec = self.spec()
        spec["pods"]["items"][0]["content"] = "next"
        xml = "".join(face.build(spec).parts)
        self.assertIn("NextPrayerComplicationService", xml)
        spec["pods"]["items"][0]["content"] = "no-such-thing"
        xml = "".join(face.build(spec).parts)
        self.assertEqual(xml.count("<ComplicationSlot "), 4)

    def test_every_studio_shape_builds(self):
        for shape in ("circle", "half", "square", "drop", "petal"):
            spec = self.spec()
            spec["pods"]["shape"] = shape
            xml = "".join(face.build(spec).parts)
            self.assertIn("pod0", xml, shape)

    def test_half_pod_is_a_circle_centred_on_the_rim(self):
        spec = self.spec()
        spec["pods"].update(shape="half", positions=[180], items=[{"content": "none"}])
        xml = "".join(face.build(spec).parts)
        r = 36 * face.K
        self.assertIn(f'<Ellipse x="{225 - r:.1f}" y="{450 - r:.1f}"', xml)

    def test_rejects_out_of_screen_pods(self):
        spec = self.spec()
        spec["pods"]["distance"] = 215
        with self.assertRaisesRegex(ValueError, "beyond the screen"):
            face.build(spec)

    def test_rejects_slot_overflow(self):
        spec = self.spec()
        spec["pods"]["positions"] = [0] * 9
        spec["pods"]["items"] = [{"content": "steps"}] * 9
        with self.assertRaisesRegex(ValueError, "not enough complication slots"):
            face.build(spec)


if __name__ == "__main__":
    unittest.main()
