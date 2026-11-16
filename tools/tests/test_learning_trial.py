import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from learning_trial import extract_epub, split_sections, parse_answer

class TrialTest(unittest.TestCase):
    def book(self, directory, bad=False):
        p=Path(directory)/'synthetic.epub'
        with zipfile.ZipFile(p,'w') as z:
            z.writestr('META-INF/container.xml','<container><rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles></container>')
            z.writestr('OEBPS/book.opf','<package><manifest><item id="a" href="a.xhtml"/><item id="b" href="b.xhtml"/></manifest><spine><itemref idref="b"/><itemref idref="a"/></spine></package>')
            z.writestr('OEBPS/a.xhtml','<html><body><p>First</p><script>SECRET</script></body></html>')
            z.writestr('OEBPS/b.xhtml','<html><body>Second &amp; more</body></html>')
            if bad:z.writestr('../unsafe','bad')
        return p
    def test_spine_order_and_script_exclusion(self):
        with tempfile.TemporaryDirectory() as d:
            parts=extract_epub(self.book(d))
            self.assertEqual([p['text'] for p in parts],['Second & more','First'])
    def test_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):extract_epub(self.book(d,True))
    def test_chunks_preserve_text_and_bounds(self):
        text='a sentence. '*5000
        chunks=split_sections([{'location':'chapter:1','text':text}])
        self.assertTrue(all(len(c['text'])<=12000 for c in chunks))
        self.assertEqual(''.join(c['text'] for c in chunks),text)
    def test_truncated_or_empty_answer_is_not_success(self):
        for x in [{},{'candidates':[{'finishReason':'MAX_TOKENS','content':{'parts':[{'text':'partial'}]}}]}]:
            with self.assertRaises(ValueError):parse_answer(x)

if __name__=='__main__':unittest.main()
