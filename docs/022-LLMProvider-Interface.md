# LLMProvider Interface

Responsabilidades

- health()
- listModels()
- chat()
- stream()
- cancel()

Nunca depender de detalhes do Ollama.

Toda implementação deve seguir este contrato.
