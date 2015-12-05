object Dot {
  private def colourForName(name: String): Option[String] = {
    name.toLowerCase match {
      case "red" => Some("red")
      case "green" => Some("yellowgreen")
      case "blue" => Some("blue")
      case "black" => Some("black")
      case "white" => Some("white")
      case "yellow" => Some("yellow")
      case _ => None
    }
  }

  private def nodeName(configIndex: Int, stateIndex: Int): String = {
    "s_" + configIndex + "_" + stateIndex
  }

  private def singleState(name: String, configIndex: Int, stateIndex: Int, colour: Option[String]): String = {
    val (colourString, labelString) = colour match {
      case Some(col) => (s",fillcolor=$col", "")
      case None => ("", name)
    }
    val internalName = nodeName(configIndex, stateIndex)
    s"""    $internalName [shape=circle,style=filled,fixedsize=true,width=0.5,label="$labelString"$colourString]\n"""
  }

  private def makeConfiguration(configuration: Configuration,
                                configIndex: Int,
                                internalToName: Map[Int, String]): String = {

    val states = configuration.states
    val stateNames = states.map(internalToName(_))
    val statePrintData = stateNames.zipWithIndex.map{case (name, index) => (name, index, colourForName(name))}
    val nodeNames = List.range(0, states.length).map(nodeName(configIndex, _))

    val statesString = statePrintData.map{case (stateName, stateIndex, colour) =>
      singleState(stateName, configIndex, stateIndex, colour)
    }.mkString

    val rankString = if (states.length > 1) {
      "    {rank=same; " + nodeNames.mkString(" ") + "}\n" +
      "    " + nodeNames.mkString(" -> ") + " [style=invis]\n"

    } else {
      ""
    }

    "  subgraph cluster_" + configIndex + "{\n" +
      statesString +
      rankString +
      "  }\n"
  }

  private def makeGraphStructure(content: String): String = {
    "digraph Configurations {\n" +
      content +
    "}\n"
  }

  private def makeIndexedConfigurations(indexedConfigurations: List[(Configuration, Int)],
                                    internalToName: Map[Int, String]): String = {

    indexedConfigurations.map{case(config, index) => makeConfiguration(config, index, internalToName)}.mkString
  }

  def makeConfigurations(configurations: Set[Configuration], internalToName: Map[Int, String]): String = {
    val sortedAndIndexed = configurations.toList.sorted.zipWithIndex
    makeGraphStructure(makeIndexedConfigurations(sortedAndIndexed, internalToName))
  }

  private def makeTransitionStates(states: Set[Int], internalToName: Map[Int, String]): String = {
    val stateNames = states.map(internalToName(_))
    val nameColourPair = stateNames.map(name => (colourForName(name), name))
    nameColourPair.map{
      case (Some(col), name) => "<font color=\"" + col + "\">\u25CF</font>"
      case (None, name) => name
    }.mkString(",")
  }

  def makeTransition(configFromIndex: Int,
                     configToIndex: Int,
                     stateIndex: Int,
                     rule: Rule,
                     internalToName: Map[Int, String]): String = {

    val fromState = nodeName(configFromIndex, stateIndex)
    val toState = nodeName(configToIndex, stateIndex)



    val label = rule match {
      case Unrestricted(_, _) => ""
      case qr:QuantifierRule =>
        val stateString = makeTransitionStates(qr.conditionStates, internalToName)
        val sideString = if (qr.side == Left) "L" else "R"
        " [label=<" + qr.quantifierString + "<sub>" + sideString + "</sub>(" + stateString + ")>]"
    }

    s"""  $fromState -> $toState$label\n"""
  }

  def makeConfigurationsWithTransitions(configsAndTransitions: Set[(Configuration, Int, Rule, Configuration)],
                                        internalToName: Map[Int, String],
                                        allTransitions: Boolean): String = {

    val lhs = configsAndTransitions.map{case(c, _, _, _) => c}
    val rhs = configsAndTransitions.map{case(_, _, _, c) => c}
    val allConfigurations = lhs | rhs
    val indexedConfigurations = allConfigurations.toList.sorted.zipWithIndex
    val indexMap = indexedConfigurations.toMap

    val configString = makeIndexedConfigurations(indexedConfigurations, internalToName)

    val transitionString = configsAndTransitions.map{case(from, stateIndex, rule, to) =>
      val configFromIndex = indexMap(from)
      val configToIndex = indexMap(to)
      makeTransition(configFromIndex, configToIndex, stateIndex, rule, internalToName)
    }.mkString

    makeGraphStructure(configString + transitionString)
  }
}