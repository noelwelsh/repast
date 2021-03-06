package repast

import cats.Semigroup
import cats.data.Chain
import cats.data.NonEmptyChain
import cats.syntax.semigroup

import scala.util.matching.Regex

/** A parser that produces a value of type A.
  *
  * The context is used to represent the possibility of resuming the parser with
  * additional input and, optionally, already parsed input. There are two
  * context types provided:
  *
  *   - `Complete` which is the result type for parsers that cannot be resumed.
  *   - `Resumable`, which is the result type for parsers that can be resumed.
  *
  * There are three cases within `Complete`:
  *
  *   - `Epsilon`, for a parser that failed but did not consume any input;
  *   - `Committed`, for a parser that failed after consuming some input; and
  *   - `Success`, for a parser that successfully parsed a portion of it's input
  *     and produced a value of type `A`.
  *
  * `Resumable` adds an additional cause for a parser that can be resumed.
  *
  * Parsers can be resumed if they accept their complete input but do not parse
  * it. In other words, a parser that `Committed` to its entire input.
  * Additionally, the programmer must indicate that a parser can be resumed
  * using the `resumable` method.
  *
  * Resumption is quite limited. The original input is not reparsed on
  * resumption. Parsing starts again on the new input and hence a parser cannot
  * carry state across resumption boundaries.
  */
enum Parser[A] {
  import Parser.*

  def *>[B](that: Parser[B]): Parser[B] =
    (this.void ~ that).map(_.apply(1))

  def *>[S, B](that: Suspendable[S, B]): Suspendable[S, B] =
    (this.void ~ that).map(_.apply(1))

  def <*[B](that: Parser[B]): Parser[A] =
    (this ~ that.void).map(_.apply(0))

  def <*[S, B](that: Suspendable[S, B]): Suspendable[S, A] =
    (this ~ that.void).map(_.apply(0))

  def ~[B](that: Parser[B]): Parser[(A, B)] =
    Product(this, that)

  def ~[S, B](that: Suspendable[S, B]): Suspendable[S, (A, B)] =
    Suspendable.fromParser(this) ~ that

  def as[B](b: B): Parser[B] =
    this.map(_ => b)

  /** Repeat this parser one or more times */
  def rep: Parser[NonEmptyChain[A]] =
    Rep(this)

  /** Repeat this parser at least count times. Count must be >= 1 */
  def min(count: Int): Parser[NonEmptyChain[A]] =
    Rep(this, min = count)

  /** Repeat this parser at least once and no more than count times. Count must
    * be >= 1
    */
  def max(count: Int): Parser[NonEmptyChain[A]] =
    Rep(this, max = count)

  def map[B](f: A => B): Parser[B] =
    Map(this, f)

  def orElse(that: Parser[A]): Parser[A] =
    OrElse(this, that)

  def advance[S]: Suspendable[S, A] =
    Suspendable.Advance(this)

  def resume(using
      semigroup: Semigroup[A],
      ev: String =:= A
  ): Suspendable[A, A] =
    Suspendable.Resume(this, str => ev(str), semigroup)

  def resumeWith(f: String => A)(using
      semigroup: Semigroup[A]
  ): Suspendable[A, A] =
    Suspendable.Resume(this, f, semigroup)

  def commit[S]: Suspendable[S, A] =
    Suspendable.Commit(this)

  def void: Parser[Unit] =
    Void(this)

  def parse(input: String, offset: Int = 0): Result[A] = {
    import Result.*

    this match {
      case Character(ch) =>
        if offset == input.size then Epsilon(input, offset)
        else if input(offset) == ch then Success(ch, input, offset, offset + 1)
        else Epsilon(input, offset)

      case CharacterIn(chars) =>
        if offset == input.size then Epsilon(input, offset)
        else
          val ch = input(offset)
          if chars.contains(ch) then Success(ch, input, offset, offset + 1)
          else Epsilon(input, offset)

      case CharacterWhere(p) =>
        if offset == input.size then Epsilon(input, offset)
        else
          val ch = input(offset)
          if p(ch) then Success(ch, input, offset, offset + 1)
          else Epsilon(input, offset)

      case CharactersWhile(p, empty, through) =>
        def loop(idx: Int): Result[A] =
          if idx == input.size then
            Continue(input.substring(offset, idx), input, offset)
          else if p(input(idx)) then loop(idx + 1)
          else if idx == offset then
            if empty then
              Success(input.substring(offset, idx), input, offset, idx)
            else Epsilon(input, offset)
          else if through then
            Success(input.substring(offset, idx + 1), input, offset, idx + 1)
          else Success(input.substring(offset, idx), input, offset, idx)

        loop(offset)

      case CharactersUntilRegexOrEnd(regex, empty, through) =>
        val matcher = regex.pattern.matcher(input)
        val found = matcher.find(offset)
        if found then
          val idx = matcher.start()
          if through then
            val stop = matcher.end()
            Success(input.substring(offset, stop), input, offset, stop)
          else if idx == offset then
            if empty then
              Success(input.substring(offset, idx), input, offset, idx)
            else Epsilon(input, offset)
          else Success(input.substring(offset, idx), input, offset, idx)
        else Continue(input.substring(offset), input, offset)

      case CharactersUntilTerminator(ts, empty, through) =>
        var idx = -1
        var nextOffset = -1
        ts.foreach { t =>
          val i = input.indexOf(t, offset)
          if (i != -1) && ((idx == -1) || (i < idx)) then
            idx = i
            nextOffset = idx + t.size
          ()
        }
        if idx == -1 then Committed(input, offset, input.size)
        else if through then
          Success(
            input.substring(offset, nextOffset),
            input,
            offset,
            nextOffset
          )
        else if idx == offset then
          if empty then
            Success(input.substring(offset, idx), input, offset, idx)
          else Epsilon(input, offset)
        else Success(input.substring(offset, idx), input, offset, idx)

      case CharactersUntilTerminatorOrEnd(ts, empty, through) =>
        var idx = -1
        var nextOffset = -1
        ts.foreach { t =>
          val i = input.indexOf(t, offset)
          if (i != -1) && ((idx == -1) || (i < idx)) then
            idx = i
            nextOffset = idx + t.size
          ()
        }
        if idx == -1 then Continue(input.substring(offset), input, offset)
        else if through then
          Success(
            input.substring(offset, nextOffset),
            input,
            offset,
            nextOffset
          )
        else if idx == offset then
          if empty then
            Success(input.substring(offset, idx), input, offset, idx)
          else Epsilon(input, offset)
        else Success(input.substring(offset, idx), input, offset, idx)

      case End =>
        if offset == input.size then Success("", input, offset, input.size)
        else Epsilon(input, offset)

      case Exactly(s) =>
        if input.startsWith(s, offset) then
          Success(s, input, offset, offset + s.size)
        else Epsilon(input, offset)

      case Map(s, f) =>
        s.parse(input, offset) match {
          case Epsilon(i, s)       => Epsilon(i, s)
          case Committed(i, s, o)  => Committed(i, s, o)
          case Continue(a, i, s)   => Continue(f(a), i, s)
          case Success(a, i, s, o) => Success(f(a), i, s, o)
        }

      case OrElse(l, r) =>
        l.parse(input, offset) match {
          case Epsilon(i, s)       => r.parse(i, s)
          case Committed(i, s, o)  => Committed(i, s, o)
          case Continue(a, i, s)   => Continue(a, i, s)
          case Success(a, i, s, o) => Success(a, i, s, o)
        }

      case OneOf(parsers) =>
        def loop[A](parsers: List[Parser[A]]): Result[A] =
          parsers match {
            case Nil => Epsilon(input, offset)
            case p :: ps =>
              p.parse(input, offset) match {
                case Epsilon(_, _)       => loop(ps)
                case Committed(i, s, o)  => Committed(i, s, o)
                case Continue(a, i, s)   => Continue(a, i, s)
                case Success(a, i, s, o) => Success(a, i, s, o)
              }
          }

        loop(parsers)

      case Product(l, r) =>
        l.parse(input, offset) match {
          case Epsilon(i, s)      => Epsilon(i, s)
          case Committed(i, s, o) => Committed(i, s, o)
          case Continue(a, i, s) =>
            r.parse(i, i.size) match {
              case Epsilon(i, s)       => Epsilon(i, s)
              case Committed(i, s, o)  => Committed(i, s, o)
              case Continue(b, i, _)   => Continue((a, b), i, s)
              case Success(b, i, _, o) => Success((a, b), i, s, o)
            }
          case Success(a, i, s, o) =>
            r.parse(i, o) match {
              case Epsilon(i, s)       => Epsilon(i, s)
              case Committed(i, s, o)  => Committed(i, s, o)
              case Continue(b, i, _)   => Continue((a, b), i, s)
              case Success(b, i, _, o) => Success((a, b), i, s, o)
            }
        }

      case r: Rep[a] =>
        def loop(
            accum: Success[NonEmptyChain[a]],
            count: Int
        ): Result[NonEmptyChain[a]] =
          // Don't allow infinite loops on parsers that don't make progress
          if accum.offset == accum.input.size then accum
          else if count > r.min && count >= r.max then accum
          else
            r.source.parse(accum.input, accum.offset) match {
              case Epsilon(i, s) =>
                if count < r.min then Epsilon(i, s)
                else accum
              case Committed(i, s, o) =>
                if count < r.min then Committed(i, s, o)
                else accum
              case Continue(a, i, s) => Continue(accum.result :+ a, i, s)
              case Success(a, i, _, o) =>
                loop(
                  Success(accum.result :+ a, i, accum.start, o),
                  count + 1
                )
            }

        r.source.parse(input, offset) match {
          case Epsilon(i, s)      => Epsilon(i, s)
          case Committed(i, s, o) => Committed(i, s, o)
          case Continue(a, i, s)  => Continue(NonEmptyChain.of(a), i, s)
          case Success(a, i, s, o) =>
            loop(Success(NonEmptyChain.of(a), i, s, o), 1)
        }

      case StringIn(s) =>
        var found = false
        var length = -1
        var matched: String = null

        s.foreach(str =>
          if (str.size > length) && input.startsWith(str, offset) then
            found = true
            matched = str
            length = str.size
        )
        if found then Success(matched, input, offset, offset + length)
        else Epsilon(input, offset)

      case Void(p) =>
        p.parse(input, offset) match {
          case Epsilon(i, s)       => Epsilon(i, s)
          case Committed(i, s, o)  => Committed(i, s, o)
          case Continue(a, i, s)   => Continue((), i, s)
          case Success(a, i, s, o) => Success((), i, s, o)
        }
    }
  }

  def parseOrExn(input: String): A =
    this.parse(input, 0) match {
      case Result.Epsilon(i, o) =>
        throw new IllegalStateException(
          s"Parser had an epsilon failure on input\n${i}\nat offset ${o}"
        )
      case Result.Committed(i, s, o) =>
        throw new IllegalStateException(
          s"Parser had a committed failure on input\n${i}\nstarting at offset ${s} and finishing at offset ${o}"
        )
      case Result.Continue(a, _, _)   => a
      case Result.Success(a, _, _, _) => a
    }

  case Character(char: Char) extends Parser[Char]
  case CharacterIn(chars: List[Char]) extends Parser[Char]
  case CharacterWhere(predicate: Char => Boolean) extends Parser[Char]
  // Empty is true if an empty match should be considered a success instead of
  // an epsilon failure.
  //
  // Through is true if the element that terminates the match should be included
  // in the result.
  case CharactersWhile(
      predicate: Char => Boolean,
      empty: Boolean,
      through: Boolean
  ) extends Parser[String]
  // Empty is true if an empty match should be considered a success instead of
  // an epsilon failure.
  //
  // Through is true if the element that terminates the match should be included
  // in the result.
  case CharactersUntilTerminator(
      terminators: Seq[String],
      empty: Boolean,
      through: Boolean
  ) extends Parser[String]
  // Empty is true if an empty match should be considered a success instead of
  // an epsilon failure.
  //
  // Through is true if the element that terminates the match should be included
  // in the result.
  case CharactersUntilTerminatorOrEnd(
      terminators: Seq[String],
      empty: Boolean,
      through: Boolean
  ) extends Parser[String]
  // Empty is true if an empty match should be considered a success instead of
  // an epsilon failure.
  //
  // Through is true if the element that terminates the match should be included
  // in the result.
  case CharactersUntilRegexOrEnd(regex: Regex, empty: Boolean, through: Boolean)
      extends Parser[String]
  case Exactly(expected: String) extends Parser[String]
  case Product[A, B](left: Parser[A], right: Parser[B]) extends Parser[(A, B)]
  case Map[A, B](source: Parser[A], f: A => B) extends Parser[B]
  case OrElse(left: Parser[A], right: Parser[A])
  case OneOf(parsers: List[Parser[A]])
  case Rep[A](source: Parser[A], min: Int = 1, max: Int = Int.MaxValue)
      extends Parser[NonEmptyChain[A]]
  case StringIn(strings: Iterable[String]) extends Parser[String]
  case Void(parser: Parser[A]) extends Parser[Unit]
  case End extends Parser[String]
}
object Parser {

  /** Parse the given character. */
  def char(char: Char): Parser[Char] =
    Parser.Character(char)

  /** Parse one of the given characters. */
  def charIn(char: Char, chars: Char*): Parser[Char] =
    Parser.CharacterIn((char +: chars).toList)

  /** Parse a single character if the predicate is true. */
  def charWhere(predicate: Char => Boolean): Parser[Char] =
    Parser.CharacterWhere(predicate)

  /** Parses one or more characters while the predicate succeeds.
    */
  def charsWhile(predicate: Char => Boolean): Parser[String] =
    Parser.CharactersWhile(predicate, false, false)

  /** Parse one or more characters until the predicates is true. */
  def charsUntil(predicate: Char => Boolean): Parser[String] =
    charsWhile(ch => !predicate(ch))

  /** Parse one or more characters until the first match of the regular
    * expression or the end of the input
    */
  def charsUntilRegexOrEnd(regex: Regex): Parser[String] =
    CharactersUntilRegexOrEnd(regex, false, false)

  /** Parse one or more characters through the first match of the regular
    * expression or the end of the input
    */
  def charsThroughRegexOrEnd(regex: Regex): Parser[String] =
    CharactersUntilRegexOrEnd(regex, false, true)

  /** Parse one or more characters until the first example of one of the
    * terminators
    */
  def charsUntilTerminator(terminators: String*): Parser[String] =
    CharactersUntilTerminator(terminators, false, false)

  /** Parse one or more characters through the first example of one of the
    * terminators
    */
  def charsThroughTerminator(terminators: String*): Parser[String] =
    CharactersUntilTerminator(terminators, false, true)

  /** Parse one or more characters until the first example of one of the
    * terminators or the end of the input
    */
  def charsUntilTerminatorOrEnd(terminators: String*): Parser[String] =
    CharactersUntilTerminatorOrEnd(terminators, false, false)

  /** Parse one or more characters through the first example of one of the
    * terminators or the end of the input
    */
  def charsThroughTerminatorOrEnd(terminators: String*): Parser[String] =
    CharactersUntilTerminatorOrEnd(terminators, false, true)

  /** Matches the end of the input */
  val end: Parser[String] =
    End

  /** Parse in order */
  def oneOf[A](parsers: List[Parser[A]]): Parser[A] =
    OneOf(parsers)

  /** Parse exactly the given string */
  def string(string: String): Parser[String] =
    Exactly(string)

  /** Parse the longest matching string amongst the given strings */
  def stringIn(strings: Iterable[String]): Parser[String] =
    StringIn(strings)

  /** The result type for a Parser
    */
  enum Result[+A] {

    def isSuccess: Boolean =
      this match {
        case e: Epsilon[A]   => false
        case c: Committed[A] => false
        case c: Continue[A]  => true
        case s: Success[A]   => true
      }

    def isFailure: Boolean =
      !isSuccess

    /** Parsed nothing starting at the given position in the input */
    case Epsilon(input: String, start: Int)

    /** Parsed up to and including one character before the given offset and
      * failed
      */
    case Committed(input: String, start: Int, offset: Int)

    /** Successfully parsed up to the end of input and could continue parsing if
      * more input was made available.
      */
    case Continue(result: A, input: String, start: Int)

    /** Successfully parsed input up to and including one character before the
      * given offset as result
      */
    case Success(result: A, input: String, start: Int, offset: Int)
  }
}
