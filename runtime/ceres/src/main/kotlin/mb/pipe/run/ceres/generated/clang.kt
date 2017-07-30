package mb.pipe.run.ceres.generated

import com.google.inject.Binder
import com.google.inject.Module
import mb.ceres.BuildContext
import mb.ceres.Builder
import mb.ceres.bindBuilder
import mb.ceres.builderMapBinder
import mb.ceres.impl.HashPathStamper
import mb.ceres.impl.ModifiedPathStamper
import mb.pipe.run.ceres.util.Tuple3
import mb.pipe.run.ceres.util.list
import mb.pipe.run.ceres.util.plus
import mb.vfs.path.PPath
import mb.vfs.path.PPathImpl
import mb.vfs.path.PPaths
import java.nio.file.Paths

class main : Builder<ArrayList<String>, String> {
  override val id = "main"
  override fun BuildContext.build(input: ArrayList<String>): String {
    val files: ArrayList<PPath> = list(PPathImpl(Paths.get("./src/lib.c")) as PPath, PPathImpl(Paths.get("./test/check_lib.c")) as PPath)
    val includeDirs = list(PPathImpl(Paths.get("./src/")) as PPath)
    val objectFiles = files.map { file -> requireOutput(compile::class.java, compile.Input(file, includeDirs, input)) }.toCollection(ArrayList<PPath>())
    val linkFlags = list("-lc", "-lcrt1.10.5.o", "-lcheck")
    val testExe = requireOutput(link::class.java, link.Input(objectFiles, PPathImpl(Paths.get("./bin/test")) as PPath, linkFlags))
    return requireOutput(test::class.java, testExe)
  }
}

class compile : Builder<compile.Input, PPath> {
  data class Input(val file: PPath, val includeDirs: ArrayList<PPath>, val flags: ArrayList<String>) : Tuple3<PPath, ArrayList<PPath>, ArrayList<String>> {
    constructor(tuple: Tuple3<PPath, ArrayList<PPath>, ArrayList<String>>) : this(tuple.component1(), tuple.component2(), tuple.component3())
  }

  override val id = "compile"
  override fun BuildContext.build(input: compile.Input): PPath {
    require(input.file, ModifiedPathStamper())
    input.includeDirs.map { dir -> require(dir, ModifiedPathStamper(PPaths.patternPathMatcher("*.h"))) }.forEach { }
    val objectFile = input.file.replaceExtension("o")
    requireOutput(mb.pipe.run.ceres.process.Execute::class.java, list("clang") + "${input.file}" + input.includeDirs.map { dir -> "-I${dir}" }.toCollection(ArrayList<String>()) + "-o${objectFile}" + "-c" + "-MMD" + input.flags)
    val depFile = input.file.replaceExtension("d")
    requireOutput(mb.pipe.run.ceres.clang.ExtractCompileDeps::class.java, depFile).map { dep -> require(dep, HashPathStamper()) }.forEach { }
    return generate(objectFile, HashPathStamper())
  }
}

class link : Builder<link.Input, PPath> {
  data class Input(val inputFiles: ArrayList<PPath>, val outputFile: PPath, val flags: ArrayList<String>) : Tuple3<ArrayList<PPath>, PPath, ArrayList<String>> {
    constructor(tuple: Tuple3<ArrayList<PPath>, PPath, ArrayList<String>>) : this(tuple.component1(), tuple.component2(), tuple.component3())
  }

  override val id = "link"
  override fun BuildContext.build(input: link.Input): PPath {
    input.inputFiles.map { file -> require(file, HashPathStamper()) }.forEach { }
    requireOutput(mb.pipe.run.ceres.process.Execute::class.java, list("ld") + input.inputFiles.map { file -> "${file}" }.toCollection(ArrayList<String>()) + "-o" + "${input.outputFile}" + input.flags)
    return generate(input.outputFile, HashPathStamper())
  }
}

class test : Builder<PPath, String> {
  override val id = "test"
  override fun BuildContext.build(input: PPath): String {
    require(input, HashPathStamper())
    val (testReport, _) = requireOutput(mb.pipe.run.ceres.process.Execute::class.java, list("${input}"))
    return testReport
  }
}


class CeresBuilderModule : Module {
  override fun configure(binder: Binder) {
    val builders = binder.builderMapBinder()

    binder.bindBuilder<test>(builders, "test")
    binder.bindBuilder<link>(builders, "link")
    binder.bindBuilder<compile>(builders, "compile")
    binder.bindBuilder<main>(builders, "main")
  }
}