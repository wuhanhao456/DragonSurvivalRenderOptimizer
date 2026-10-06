"""Resolve the public artifact name while retaining legacy mod/config identifiers."""

def artifact_name(project):
    properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    return properties.get('mod_archive_name', 'dsbr') + '-' + properties['mod_version']
