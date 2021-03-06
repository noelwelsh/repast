package repast

import cats.data.NonEmptyChain
import cats.implicits.*
import munit.FunSuite

class ParserSuite extends FunSuite {
  import Parser.Result

  test("Parser.char") {
    assertEquals(Parser.char('a').parseOrExn("a"), 'a')
    assert(Parser.char('a').parse("b").isFailure)
  }

  test("Parser.charIn") {
    assertEquals(Parser.charIn('a').parseOrExn("a"), 'a')
    assertEquals(Parser.charIn('a', 'b', 'c').parseOrExn("c"), 'c')
    assert(Parser.charIn('a').parse("b").isFailure)
  }

  test("Parser.charWhere") {
    assertEquals(Parser.charWhere(_.isDigit).parseOrExn("1"), '1')
    assert(Parser.charWhere(_.isDigit).parse("b").isFailure)
  }

  test("Parser.charsWhile") {
    assertEquals(
      Parser.charsWhile(_.isDigit).parse("123abc"),
      Result.Success("123", "123abc", 0, 3)
    )
    assertEquals(
      Parser.charsWhile(_.isDigit).parse("123"),
      Result.Continue("123", "123", 0)
    )
    assertEquals(
      Parser.charsWhile(_.isDigit).parse("abc"),
      Result.Epsilon("abc", 0)
    )
  }

  test("Parser.charsUntilRegexOrEnd") {
    val parser = Parser.charsUntilRegexOrEnd("1[2-4]+".r)

    val endsInTerminator = "abcd13"
    assertEquals(
      parser.parse(endsInTerminator),
      Result.Success("abcd", endsInTerminator, 0, 4)
    )

    val endsWithoutTerminator1 = "abcd"
    assertEquals(
      parser.parse(endsWithoutTerminator1),
      Result.Continue(endsWithoutTerminator1, endsWithoutTerminator1, 0)
    )

    val endsWithoutTerminator2 = "abcd15"
    assertEquals(
      parser.parse(endsWithoutTerminator2),
      Result.Continue(endsWithoutTerminator2, endsWithoutTerminator2, 0)
    )

    val endsAtFirstMatch = "abcd12ef12"
    assertEquals(
      parser.parse(endsAtFirstMatch),
      Result.Success("abcd", endsAtFirstMatch, 0, 4)
    )

    val endsImmediately = "12"
    assertEquals(
      parser.parse(endsImmediately),
      Result.Epsilon(endsImmediately, 0)
    )
  }

  test("Parser.charsThroughRegexOrEnd") {
    val parser = Parser.charsThroughRegexOrEnd("1[2-4]+".r)

    val endsInTerminator = "abcd13"
    assertEquals(
      parser.parse(endsInTerminator),
      Result.Success("abcd13", endsInTerminator, 0, 6)
    )

    val endsWithoutTerminator1 = "abcd"
    assertEquals(
      parser.parse(endsWithoutTerminator1),
      Result.Continue(endsWithoutTerminator1, endsWithoutTerminator1, 0)
    )

    val endsWithoutTerminator2 = "abcd15"
    assertEquals(
      parser.parse(endsWithoutTerminator2),
      Result.Continue(endsWithoutTerminator2, endsWithoutTerminator2, 0)
    )

    val endsAtFirstMatch = "abcd12ef12"
    assertEquals(
      parser.parse(endsAtFirstMatch),
      Result.Success("abcd12", endsAtFirstMatch, 0, 6)
    )

    val endsImmediately = "12"
    assertEquals(
      parser.parse(endsImmediately),
      Result.Success(endsImmediately, endsImmediately, 0, 2)
    )

    val empty = ""
    assertEquals(parser.parse(empty), Result.Continue(empty, empty, 0))
  }

  test("Parser.charsUntilTerminator") {
    assertEquals(
      Parser.charsUntilTerminator("abc").parse("123abc"),
      Result.Success("123", "123abc", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminator("a", "bc").parse("123abc"),
      Result.Success("123", "123abc", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminator("a", "b").parse("123b"),
      Result.Success("123", "123b", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminator("abc").parse("123"),
      Result.Committed("123", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminator("abc").parse("abc"),
      Result.Epsilon("abc", 0)
    )
  }

  test("Parser.charsThroughTerminator") {
    assertEquals(
      Parser.charsThroughTerminator("abc").parse("123abc"),
      Result.Success("123abc", "123abc", 0, 6)
    )
    assertEquals(
      Parser.charsThroughTerminator("a", "bc").parse("123abc"),
      Result.Success("123a", "123abc", 0, 4)
    )
    assertEquals(
      Parser.charsThroughTerminator("a", "b").parse("123b"),
      Result.Success("123b", "123b", 0, 4)
    )
    assertEquals(
      Parser.charsThroughTerminator("abc").parse("123"),
      Result.Committed("123", 0, 3)
    )
    assertEquals(
      Parser.charsThroughTerminator("abc").parse("abc"),
      Result.Success("abc", "abc", 0, 3)
    )
  }

  test("Parser.charsUntilTerminatorOrEnd") {
    assertEquals(
      Parser.charsUntilTerminatorOrEnd("\n").parse("123abc"),
      Result.Continue("123abc", "123abc", 0)
    )
    assertEquals(
      Parser.charsUntilTerminatorOrEnd("\n").parse("123abc\n"),
      Result.Success("123abc", "123abc\n", 0, 6)
    )
    assertEquals(
      Parser.charsUntilTerminatorOrEnd(">", "<").parse("123<"),
      Result.Success("123", "123<", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminatorOrEnd(">", "<").parse("123>"),
      Result.Success("123", "123>", 0, 3)
    )
    assertEquals(
      Parser.charsUntilTerminatorOrEnd(">", "<").parse("123"),
      Result.Continue("123", "123", 0)
    )
    assertEquals(
      Parser.charsUntilTerminatorOrEnd("abc").parse("abc"),
      Result.Epsilon("abc", 0)
    )
  }

  test("Parser.charsThroughTerminatorOrEnd") {
    assertEquals(
      Parser.charsThroughTerminatorOrEnd("\n").parse("123abc"),
      Result.Continue("123abc", "123abc", 0)
    )
    assertEquals(
      Parser.charsThroughTerminatorOrEnd("\n").parse("123abc\n"),
      Result.Success("123abc\n", "123abc\n", 0, 7)
    )
    assertEquals(
      Parser.charsThroughTerminatorOrEnd(">", "<").parse("123<"),
      Result.Success("123<", "123<", 0, 4)
    )
    assertEquals(
      Parser.charsThroughTerminatorOrEnd(">", "<").parse("123>"),
      Result.Success("123>", "123>", 0, 4)
    )
    assertEquals(
      Parser.charsThroughTerminatorOrEnd(">", "<").parse("123"),
      Result.Continue("123", "123", 0)
    )
    assertEquals(
      Parser.charsThroughTerminatorOrEnd("abc").parse("abc"),
      Result.Success("abc", "abc", 0, 3)
    )
  }

  test("Parser.stringIn") {
    val p = Parser.stringIn(List("#", "##", "###"))
    assertEquals(p.parse("#"), Result.Success("#", "#", 0, 1))
    assertEquals(p.parse("##"), Result.Success("##", "##", 0, 2))
    assertEquals(p.parse("###"), Result.Success("###", "###", 0, 3))
    assertEquals(p.parse("####"), Result.Success("###", "####", 0, 3))
    assert(p.parse("abc").isFailure)
  }

  test("Parser.~") {
    val whiteSpace = Parser.charsWhile(ch => ch == ' ' || ch == '\t')
    val lineEnd = Parser.string("\n")
    val p = whiteSpace ~ lineEnd

    val ws = "    "
    val lf = "\n"
    val input = ws ++ lf

    assertEquals(whiteSpace.parse(ws), Result.Continue(ws, ws, 0))
    assertEquals(whiteSpace.parse(input), Result.Success(ws, input, 0, ws.size))
    assertEquals(lineEnd.parse(lf), Result.Success(lf, lf, 0, lf.size))
    assertEquals(p.parse(input), Result.Success((ws, lf), input, 0, input.size))
  }

  test("Parser.end") {
    val end = Parser.end
    val whiteSpace = Parser.charsWhile(ch => ch == ' ' || ch == '\t')
    val parser = whiteSpace <* end
    val input = "    "

    assertEquals(
      parser.parse(input),
      Result.Success(input, input, 0, input.size)
    )
    assertEquals(end.parse(input), Result.Epsilon(input, 0))
  }

  test("Parser.rep repeats until parser does not succeed") {
    val parser = Parser.char('a').commit.rep
    val input = "aaaa "

    assertEquals(
      parser.parse(input),
      Resumable.success(NonEmptyChain('a', 'a', 'a', 'a'), input, 0, 4)
    )
  }

  test("Parser.rep accumulates results in correct order") {
    val parser = Parser.charWhere(_.isDigit).commit.rep
    val input = "1234 "

    assertEquals(
      parser.parse(input),
      Resumable.success(NonEmptyChain('1', '2', '3', '4'), input, 0, 4)
    )
  }

  test("Parser.rep fails if parser doesn't parse at least once") {
    val parser = Parser.charWhere(_.isDigit).commit.rep
    val input = " "

    assertEquals(parser.parse(input), Resumable.epsilon(input, 0))
  }

  test("Parser.rep succeeds if parser parses at least once") {
    val parser = Parser.charWhere(_.isDigit).commit.rep
    val input = "1 "

    assertEquals(
      parser.parse(input),
      Resumable.success(NonEmptyChain('1'), input, 0, 1)
    )
  }

  test(
    "Parser.min successfully parses at least minimum number of elements"
  ) {
    val parser = Parser.char('a').commit.min(2)
    val input = "aa "

    assertEquals(
      parser.parse(input),
      Resumable.success(NonEmptyChain('a', 'a'), input, 0, 2)
    )
  }

  test(
    "Parser.min fails if it cannot parse at least minimum number of elements"
  ) {
    val parser = Parser.char('a').commit.min(2)
    val input = "a "

    assertEquals(
      parser.parse(input),
      Resumable.committed(input, 0, 1)
    )
  }

  test(
    "Parser.max successfully parses no more than max number of elements"
  ) {
    val parser = Parser.char('a').commit.max(2)
    val input = "aaaa "

    assertEquals(
      parser.parse(input),
      Resumable.success(NonEmptyChain('a', 'a'), input, 0, 2)
    )
  }
}
