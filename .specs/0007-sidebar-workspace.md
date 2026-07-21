# SPEC-0007 — Sidebar Workspace

## Objetivo
Redesenhar a barra lateral esquerda para priorizar o editor SQL e eliminar informações duplicadas.

## Princípios
- A área mais importante da aplicação é o SQL Editor.
- Nenhuma alteração pode reduzir a largura ou altura do editor sem justificativa.
- Informações permanentes devem permanecer permanentes.
- Informações temporárias não devem ocupar espaço fixo.

## Estrutura

```
Sidebar
│
├── Logo
│
├── Navigation Rail
│
├── Active Connection Card
│
└── Workspace
```

### Navigation Rail
Itens fixos:

```
Conexões
Objetos
Consultas
Histórico
Favoritos
Configurações
```

Nunca muda de tamanho.

### Active Connection Card
Sempre visível. Nunca desaparece, independentemente da seção aberta.

Contém somente:

```
● Nome
Host
Engine
Status
```

Exemplo:

```
● OPERATION_DEV
root@localhost
MySQL 8.0
Conectado
```

### Workspace
O conteúdo abaixo do card muda conforme a navegação.

Exemplo — Objetos:
```
Buscar
Árvore
...
```

Exemplo — Histórico:
```
Filtro
Lista
...
```

Exemplo — Consultas:
```
Lista
Favoritas
...
```

## Barra inferior
Eliminar. Todas as informações existentes devem migrar para outro lugar.

Status → mover para: Editor ou Card de conexão. Nunca duplicar.

## Objetivo visual
Parecer uma IDE. Não parecer um cliente tradicional de banco.

Inspirado em: VS Code, IntelliJ, Fleet, Warp.
Não inspirado em: DBeaver, Workbench, PL/SQL Developer.

## Regras

### Nunca
- Nunca mostrar "Conectado" na barra inferior.
- Nunca repetir MySQL / Conectado / Host em mais de um lugar.

### Sempre
- Sempre existir exatamente um lugar mostrando: conexão, host, banco, engine.

### Espaço
- Toda remoção de informação deve aumentar: editor SQL ou grid.
- Nunca criar espaços vazios.

## Critérios de aceite
- ✔ Card permanece visível ao trocar de módulo
- ✔ Barra inferior removida
- ✔ Nenhuma informação duplicada
- ✔ Editor ganha mais espaço
- ✔ Layout permanece responsivo
