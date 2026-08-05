import os

def replace_in_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    
    modified = content
    for old, new in replacements.items():
        modified = modified.replace(old, new)
        
    if modified != content:
        with open(filepath, 'w') as f:
            f.write(modified)
        print(f"Refactored: {filepath}")

# Refactor login-service
login_dir = "server/login-service/src/main/java/catalyst/server/login"
login_replacements = {
    "package catalyst.server.repository;": "package catalyst.server.login.repository;",
    "package catalyst.server.config;": "package catalyst.server.login.config;",
    "package catalyst.server.handler;": "package catalyst.server.login.handler;",
    "import catalyst.server.config.ServerProperties;": "import catalyst.server.login.config.ServerProperties;",
    "import catalyst.server.repository.AccountRepository;": "import catalyst.server.login.repository.AccountRepository;"
}
for root, _, files in os.walk(login_dir):
    for f in files:
        if f.endswith(".java"):
            replace_in_file(os.path.join(root, f), login_replacements)

# Refactor lobby-service
lobby_dir = "server/lobby-service/src/main/java/catalyst/server/lobby"
lobby_replacements = {
    "package catalyst.server.repository;": "package catalyst.server.lobby.repository;",
    "package catalyst.server.config;": "package catalyst.server.lobby.config;",
    "package catalyst.server.handler;": "package catalyst.server.lobby.handler;",
    "import catalyst.server.repository.CharacterRepository;": "import catalyst.server.lobby.repository.CharacterRepository;"
}
for root, _, files in os.walk(lobby_dir):
    for f in files:
        if f.endswith(".java"):
            replace_in_file(os.path.join(root, f), lobby_replacements)

# Refactor world-service
world_dir = "server/world-service/src/main/java/catalyst/server/world"
world_replacements = {
    "package catalyst.server.repository;": "package catalyst.server.world.repository;",
    "package catalyst.server.handler;": "package catalyst.server.world.handler;",
    "package catalyst.server.session;": "package catalyst.server.world.session;",
    "package catalyst.server.dispatch;": "package catalyst.server.world.dispatch;",
    "package catalyst.server.transport;": "package catalyst.server.world.transport;",
    "import catalyst.server.repository.SessionRepository;": "import catalyst.server.world.repository.SessionRepository;",
    "import catalyst.server.session.AuthTicketStore;": "import catalyst.server.world.session.AuthTicketStore;",
    "import catalyst.server.session.ZoneManager;": "import catalyst.server.world.session.ZoneManager;",
    "import catalyst.server.dispatch.MessageDispatcher;": "import catalyst.server.world.dispatch.MessageDispatcher;",
    "import catalyst.server.handler.WorldHandler;": "import catalyst.server.world.handler.WorldHandler;"
}
for root, _, files in os.walk(world_dir):
    for f in files:
        if f.endswith(".java"):
            replace_in_file(os.path.join(root, f), world_replacements)
