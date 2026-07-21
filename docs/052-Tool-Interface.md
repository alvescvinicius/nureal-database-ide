# Tool Interface

Contrato sugerido

interface Tool {

String getName();

String getDescription();

ToolResult execute(ToolRequest request);

}

As implementações não conhecem o Agent.
