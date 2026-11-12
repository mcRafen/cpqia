#!/usr/bin/env python3
"""Genera src/main/resources/static/css/palettes.css (palette «Viola», giorno e notte) dalla palette:
#0B0720  #25134D  #6A2FC8  #9D71E8  #D1B8FF  #F2E9FF
Le regole sovrascrivono il tema predefinito (verde acqua) solo con <html data-palette="viola">.
Uso: python3 tools/gen_palette.py"""
import os

DAY = dict(
    vars='''  --primary: #6A2FC8; --on-primary: #fff; --primary-container: #e4d6ff; --on-primary-container: #25134D;
  --secondary-container: #ece2ff;
  --surface: #fff; --surface-1: #f8f3ff; --surface-2: #efe6ff; --surface-3: #e3d6fb;
  --background: #F2E9FF;
  --on-surface: #1a0f35; --on-surface-var: #5d4a8f; --outline: #8a72c2; --outline-var: #dccff4;
  --error: #c9283f; --error-container: #ffdde6; --warn: #8a5600; --warn-container: #ffeabb; --ok: #0f6e4a; --ok-container: #d3f4e5;
  --c-blue: #4b5fe0; --c-purple: #6A2FC8; --c-pink: #d6486f; --c-orange: #e08a00; --c-yellow: #f2a516;
  --c-green: #1e9e6a; --c-teal: #00a3a3; --c-red: #c9283f;
  --bar: #25134D; --bar-ink: #25134D;
  --grad: linear-gradient(#25134D, #25134D);
  --shadow-1: 0 1px 2px rgba(37, 19, 77, .18), 0 1px 3px 1px rgba(37, 19, 77, .10);
  --shadow-2: 0 1px 2px rgba(37, 19, 77, .20), 0 2px 8px 2px rgba(37, 19, 77, .12);''',
    body='radial-gradient(560px 360px at 100% 64px, rgba(209, 184, 255, .60), transparent 70%), radial-gradient(640px 420px at 0% 480px, rgba(228, 214, 255, .75), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(255, 226, 200, .35), transparent 70%), var(--background)',
    appbar_shadow='0 2px 8px rgba(37, 19, 77, .35)', logo_bg='#fff', logo_fg='#25134D',
    card_shadow='0 1px 3px rgba(37, 19, 77, .10)', card_alt='#e0a030', card_alt2='var(--c-blue)',
    btn_hover='#5624a8', tonal_bg='#ffe9c7', tonal_fg='#8a4b00', tonal_hover='#ffdca6',
    outlined_border='#b79cf0', disabled_bg='rgba(26, 15, 53, .12)', disabled_fg='rgba(26, 15, 53, .38)',
    th_bg='#ece2ff', th_fg='#4a22a0', th_border='#cdb9f5', tr_hover='#f4eeff', mods_on='#e4f6ec',
    appname='#1a0f35', stepbar='linear-gradient(90deg, #d1b8ff, #ece2ff)', step_done_fg='#fff', step_active_fg='#fff',
    chip_fg='#4a3d73', chip_blue_bg='#ece2ff', chip_blue_fg='#5624a8',
    f=[('#dfe6ff', '#2b3fb0'), ('#d3f4e5', '#0f6e4a'), ('#ffeabb', '#8a5600'), ('#e8e4f3', '#4a3d73'), ('#ffdde6', '#b01e47'), ('#e1d2ff', '#4a22a0')],
    chip_filter_bg='#fff', chip_filter_on_fg='#fff', chip_f3_on='#6b5f8f',
    alert_info_bg='var(--secondary-container)', alert_info_fg='var(--on-primary-container)',
    total_b='#25134D', kv_bg='#efe6ff', kv_border='#d6c5f7', kv_small='#5d4a8f', kv_b='#25134D',
    margin_bg='#e4f6ec', margin_border='#bfe5cd', margin_b='#0d652d', terms_fg='#4a22a0', terms_border='#cdb9f5',
    dlg_head='var(--primary-container)', cgchip_bg='var(--primary)', cgchip_fg='#fff', grp_bg='#ece2ff',
    check_fg='#fff', h1='var(--on-surface)', input_fg='var(--on-surface)', nav_fg='var(--on-surface)', nav_active_fg='var(--on-primary-container)',
    nav_hover='var(--surface-2)', snack_bg='#25134D', snack_fg='#F2E9FF', tabs_hover='var(--secondary-container)',
    login='radial-gradient(circle at 15% 15%, rgba(255, 226, 200, .6) 0, transparent 40%), radial-gradient(circle at 85% 20%, rgba(209, 184, 255, .85) 0, transparent 42%), radial-gradient(circle at 70% 90%, rgba(228, 214, 255, .8) 0, transparent 45%), #F2E9FF',
    login_shadow='0 10px 40px rgba(37, 19, 77, .18)', login_logo_bg='var(--bar)', login_logo_fg='#fff',
    h1_weight='600', link_dotted='var(--primary)',
)
NIGHT = dict(
    vars='''  --primary: #9D71E8; --on-primary: #0B0720; --primary-container: #3c1f80; --on-primary-container: #D1B8FF;
  --secondary-container: #2e1a63;
  --surface: #25134D; --surface-1: #2b185a; --surface-2: #331f6a; --surface-3: #3d2a7c;
  --background: #0B0720;
  --on-surface: #F2E9FF; --on-surface-var: #bfa9ea; --outline: #7a63b5; --outline-var: #3d2a73;
  --error: #ff9db6; --error-container: #4d1d3d; --warn: #ffd36b; --warn-container: #3f3115; --ok: #6ee7b0; --ok-container: #103a33;
  --c-blue: #9dbcff; --c-purple: #9D71E8; --c-pink: #ff9db6; --c-orange: #ffd36b; --c-yellow: #ffd36b;
  --c-green: #6ee7b0; --c-teal: #5fe0d3; --c-red: #ff7a8f;
  --bar: #0B0720; --bar-ink: #D1B8FF;
  --grad: linear-gradient(#0B0720, #0B0720);
  --shadow-1: 0 1px 2px rgba(0, 0, 0, .6), 0 1px 3px 1px rgba(0, 0, 0, .4);
  --shadow-2: 0 1px 2px rgba(0, 0, 0, .6), 0 2px 8px 2px rgba(0, 0, 0, .45);''',
    body='radial-gradient(620px 420px at 100% 64px, rgba(106, 47, 200, .34), transparent 70%), radial-gradient(640px 420px at 0% 520px, rgba(106, 47, 200, .20), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(157, 113, 232, .12), transparent 70%), var(--background)',
    appbar_shadow='0 2px 8px rgba(0, 0, 0, .6)', logo_bg='#F2E9FF', logo_fg='#25134D',
    card_shadow='0 1px 3px rgba(0, 0, 0, .6)', card_alt='#ffd36b', card_alt2='var(--c-blue)',
    btn_hover='#b08af0', tonal_bg='#4a3a1c', tonal_fg='#ffd596', tonal_hover='#5a4722',
    outlined_border='#5a3fa0', disabled_bg='rgba(255, 255, 255, .10)', disabled_fg='rgba(255, 255, 255, .38)',
    th_bg='#2e1a63', th_fg='#D1B8FF', th_border='#4a3590', tr_hover='#2f1b66', mods_on='#103a33',
    appname='var(--on-surface)', stepbar='linear-gradient(90deg, #4a2d9a, #2e1a63)', step_done_fg='#04170f', step_active_fg='#0B0720',
    chip_fg='#e3d6ff', chip_blue_bg='var(--primary-container)', chip_blue_fg='#D1B8FF',
    f=[('rgba(120, 150, 255, .22)', '#a9c0ff'), ('rgba(70, 220, 150, .17)', '#6ee7b0'), ('rgba(255, 196, 60, .18)', '#ffd36b'), ('rgba(209, 184, 255, .16)', '#D1B8FF'), ('rgba(255, 107, 143, .2)', '#ff9db6'), ('rgba(157, 113, 232, .26)', '#D1B8FF')],
    chip_filter_bg='transparent', chip_filter_on_fg='#0B0720', chip_f3_on='#9D71E8',
    alert_info_bg='var(--primary-container)', alert_info_fg='var(--on-primary-container)',
    total_b='#D1B8FF', kv_bg='#2b185a', kv_border='#4a3590', kv_small='#bfa9ea', kv_b='#F2E9FF',
    margin_bg='#103a33', margin_border='#1f6a55', margin_b='#6ee7b0', terms_fg='#D1B8FF', terms_border='#4a3590',
    dlg_head='var(--primary-container)', cgchip_bg='var(--primary)', cgchip_fg='#0B0720', grp_bg='#2e1a63',
    check_fg='#0B0720', h1='var(--on-surface)', input_fg='var(--on-surface)', nav_fg='#e3d6ff', nav_active_fg='#F2E9FF',
    nav_hover='var(--surface-3)', snack_bg='#F2E9FF', snack_fg='#25134D', tabs_hover='var(--secondary-container)',
    login='radial-gradient(circle at 85% 12%, rgba(106, 47, 200, .5) 0, transparent 45%), radial-gradient(circle at 12% 90%, rgba(157, 113, 232, .22) 0, transparent 45%), #0B0720',
    login_shadow='0 10px 40px rgba(0, 0, 0, .55)', login_logo_bg='var(--primary)', login_logo_fg='#0B0720',
    h1_weight='400', link_dotted='var(--primary)',
)

def block(t, name, night):
    P = 'html[data-palette="%s"][data-palette]' % name + ('[data-theme="dark"]' if night else ':not([data-theme="dark"])')
    root = P.replace('[data-palette]', '', 1)      # variabili: specificita' piu' bassa delle regole, ma sopra :root / html[data-theme=dark]
    f = t['f']
    r = []
    r.append(f"{root} {{\n{t['vars']}\n}}")
    r.append(f"{P} body {{ background: {t['body']}; background-attachment: fixed; }}")
    r.append(f"{P} .appbar {{ background: var(--bar); box-shadow: {t['appbar_shadow']}; }}")
    r.append(f"{P} .appbar .logo, {P} .appbar .avatar {{ background: {t['logo_bg']}; color: {t['logo_fg']}; }}")
    r.append(f"{P} .pagehead h1 {{ background: none; -webkit-background-clip: border-box; background-clip: border-box; color: {t['h1']}; font-weight: {t['h1_weight']}; }}")
    r.append(f"{P} .drawer {{ background: var(--surface); border-right: 1px solid var(--outline-var); }}")
    r.append(f"{P} .drawer a {{ color: {t['nav_fg']}; }}")
    r.append(f"{P} .drawer a:hover {{ background: {t['nav_hover']}; }}")
    r.append(f"{P} .drawer a.active {{ background: var(--primary-container); color: {t['nav_active_fg']}; box-shadow: inset 3px 0 0 var(--primary); }}")
    r.append(f"{P} .drawer a.active .ic, {P} .drawer .section {{ color: var(--primary); }}")
    r.append(f"{P} .card {{ border: 1px solid var(--outline-var); border-top: 4px solid var(--primary); box-shadow: {t['card_shadow']}; }}")
    r.append(f"{P} .card:nth-of-type(3n+2) {{ border-top-color: {t['card_alt']}; }}")
    r.append(f"{P} .card:nth-of-type(3n+3) {{ border-top-color: {t['card_alt2']}; }}")
    r.append(f"{P} .card h2 {{ color: var(--on-surface); }}")
    r.append(f"{P} .btn {{ background: var(--primary); color: var(--on-primary); }}")
    r.append(f"{P} .btn:hover {{ background: {t['btn_hover']}; }}")
    r.append(f"{P} .btn.tonal {{ background: {t['tonal_bg']}; color: {t['tonal_fg']}; }}")
    r.append(f"{P} .btn.tonal:hover {{ background: {t['tonal_hover']}; }}")
    r.append(f"{P} .btn.outlined {{ background: transparent; color: var(--primary); border-color: {t['outlined_border']}; }}")
    r.append(f"{P} .btn.text {{ background: transparent; color: var(--primary); }}")
    r.append(f"{P} .btn.outlined:hover, {P} .btn.text:hover {{ background: var(--secondary-container); }}")
    r.append(f"{P} .btn.danger {{ background: var(--c-red); color: #fff; }}")
    r.append(f"{P} .btn.text.danger {{ background: transparent; color: var(--error); }}")
    r.append(f"{P} .btn.outlined.danger, {P} .btn.tonal.danger {{ background: transparent; color: var(--error); }}")
    r.append(f"{P} .btn:disabled, {P} .btn[aria-disabled=true], {P} .btn.tonal:disabled {{ background: {t['disabled_bg']}; color: {t['disabled_fg']}; }}")
    r.append(f"{P} .btn.icon {{ background: transparent; color: var(--on-surface-var); }}")
    r.append(f"{P} table.t th {{ background: {t['th_bg']}; color: {t['th_fg']}; border-bottom: 2px solid {t['th_border']}; }}")
    r.append(f"{P} table.t tbody tr:hover > td {{ background: {t['tr_hover']}; }}")
    r.append(f"{P} table.mods tr.on td {{ background: {t['mods_on']}; }}")
    r.append(f"{P} .apps tr.app-row.sel > td, {P} .apps tr.app-row.sel:hover > td {{ background: var(--primary-container); }}")
    r.append(f"{P} .apps tr.detail > td {{ background: var(--surface-1); }}")
    r.append(f"{P} .detail-inner {{ border-left: 4px solid var(--primary); }} {P} .detail-inner h4 {{ color: var(--primary); }}")
    r.append(f"{P} .apps .appname {{ color: {t['appname']}; }}")
    r.append(f"{P} .step .dot {{ background: var(--surface-3); color: var(--on-surface-var); }}")
    r.append(f"{P} .step.active .dot {{ background: var(--primary); color: {t['step_active_fg']}; box-shadow: 0 0 0 4px var(--primary-container); }}")
    r.append(f"{P} .step.done .dot {{ background: var(--c-green); color: {t['step_done_fg']}; }}")
    r.append(f"{P} .stepper .bar {{ background: {t['stepbar']}; }}")
    r.append(f"{P} .chip {{ background: var(--surface-3); color: {t['chip_fg']}; }}")
    r.append(f"{P} .chip.blue {{ background: {t['chip_blue_bg']}; color: {t['chip_blue_fg']}; }}")
    for i, (bg, fg) in enumerate(f):
        r.append(f"{P} .chip.f{i} {{ background: {bg}; color: {fg}; }}")
    r.append(f"{P} .chip.ok {{ background: var(--ok-container); color: var(--ok); }} {P} .chip.warn {{ background: var(--warn-container); color: var(--warn); }} {P} .chip.err {{ background: var(--error-container); color: var(--error); }}")
    r.append(f"{P} .chip.filter {{ background: {t['chip_filter_bg']}; border-color: var(--outline-var); }}")
    r.append(f"{P} .chip.filter.on {{ background: var(--primary); color: {t['chip_filter_on_fg']}; }}")
    r.append(f"{P} .chip.filter.f0.on {{ background: var(--c-blue); }} {P} .chip.filter.f1.on {{ background: var(--c-green); }} {P} .chip.filter.f2.on {{ background: var(--c-orange); }} {P} .chip.filter.f3.on {{ background: {t['chip_f3_on']}; }} {P} .chip.filter.f4.on {{ background: var(--c-pink); }} {P} .chip.filter.f5.on {{ background: var(--c-teal); }}")
    r.append(f"{P} .alert.info {{ background: {t['alert_info_bg']}; color: {t['alert_info_fg']}; }}")
    r.append(f"{P} .totalbar {{ background: var(--surface); border-top: 3px solid var(--primary); }}")
    r.append(f"{P} .totalbar .kv b {{ color: {t['total_b']}; }}")
    r.append(f"{P} .kv-box {{ background: {t['kv_bg']}; border-color: {t['kv_border']}; }} {P} .kv-box small {{ color: {t['kv_small']}; }} {P} .kv-box b {{ color: {t['kv_b']}; }}")
    r.append(f"{P} .kv-box.margin {{ background: {t['margin_bg']}; border-color: {t['margin_border']}; }} {P} .kv-box.margin b {{ color: {t['margin_b']}; }}")
    r.append(f"{P} .terms-grid .th, {P} .mlabel {{ color: {t['terms_fg']}; border-bottom-color: {t['terms_border']}; }}")
    r.append(f"{P} dialog .dlg-head {{ background: {t['dlg_head']}; }}")
    r.append(f"{P} .cg-chip {{ background: {t['cgchip_bg']}; color: {t['cgchip_fg']}; }}")
    r.append(f"{P} .cg-table tr.cg-grp td, {P} .cg-table tfoot td, {P} .cg-groupbar.on {{ background: {t['grp_bg']}; }}")
    r.append(f"{P} .cg-table tr.cg-picked > td {{ background: var(--secondary-container); }}")
    r.append(f"{P} .check input:checked {{ background: var(--primary); border-color: var(--primary); }} {P} .check input:checked::after {{ border-color: {t['check_fg']}; }}")
    r.append(f"{P} input, {P} select, {P} textarea {{ color: {t['input_fg']}; }}")
    r.append(f"{P} .field input:focus, {P} .field select:focus, {P} .field textarea:focus {{ border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }}")
    r.append(f"{P} .chip.rev-now {{ background: var(--primary); color: var(--on-primary); }}")
    r.append(f"{P} .snackbar {{ background: {t['snack_bg']}; color: {t['snack_fg']}; }}")
    r.append(f"{P} .loginpage {{ background: {t['login']}; }} {P} .logincard {{ box-shadow: {t['login_shadow']}; }} {P} .logincard .logo {{ background: {t['login_logo_bg']}; color: {t['login_logo_fg']}; }}")
    r.append(f"{P} .login-theme > button:hover {{ background: var(--secondary-container); }}")
    return '\n'.join(r)


# ---------------- palette «Foresta»: #0F1F17  #1E3A2E  #3E5A3F  #6B7F67  #C7CBB8  #3D4337 ----------------
F_DAY = dict(DAY)
F_DAY.update(
    vars='''  --primary: #3E5A3F; --on-primary: #fff; --primary-container: #dde5cf; --on-primary-container: #1E3A2E;
  --secondary-container: #e4e9d8;
  --surface: #fff; --surface-1: #f5f6ee; --surface-2: #ecefdf; --surface-3: #dfe3cf;
  --background: #e9ebdf;
  --on-surface: #0F1F17; --on-surface-var: #4a5646; --outline: #6B7F67; --outline-var: #d3d7c4;
  --error: #c9283f; --error-container: #ffdde6; --warn: #8a5600; --warn-container: #ffeabb; --ok: #0f6e4a; --ok-container: #d3f4e5;
  --c-blue: #3a7ab5; --c-purple: #3E5A3F; --c-pink: #d6486f; --c-orange: #e08a00; --c-yellow: #f2a516;
  --c-green: #3E5A3F; --c-teal: #5f8f7a; --c-red: #c9283f;
  --bar: #1E3A2E; --bar-ink: #1E3A2E;
  --grad: linear-gradient(#1E3A2E, #1E3A2E);
  --shadow-1: 0 1px 2px rgba(15, 31, 23, .18), 0 1px 3px 1px rgba(15, 31, 23, .10);
  --shadow-2: 0 1px 2px rgba(15, 31, 23, .20), 0 2px 8px 2px rgba(15, 31, 23, .12);''',
    body='radial-gradient(560px 360px at 100% 64px, rgba(199, 203, 184, .75), transparent 70%), radial-gradient(640px 420px at 0% 480px, rgba(107, 127, 103, .22), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(255, 226, 200, .30), transparent 70%), var(--background)',
    appbar_shadow='0 2px 8px rgba(15, 31, 23, .35)', logo_bg='#fff', logo_fg='#1E3A2E', card_shadow='0 1px 3px rgba(15, 31, 23, .10)',
    btn_hover='#2f4730', outlined_border='#9db08f', disabled_bg='rgba(15, 31, 23, .12)', disabled_fg='rgba(15, 31, 23, .38)',
    th_bg='#e4e9d8', th_fg='#2e4a35', th_border='#c7cbb8', tr_hover='#f1f3e8', mods_on='#e4f1e4', appname='#0F1F17',
    stepbar='linear-gradient(90deg, #c7cbb8, #e4e9d8)', chip_fg='#3D4337', chip_blue_bg='#e4e9d8', chip_blue_fg='#2e4a35',
    f=[('#dde8f5', '#2a5a8f'), ('#d3f4e5', '#0f6e4a'), ('#ffeabb', '#8a5600'), ('#e4e6da', '#3D4337'), ('#ffdde6', '#b01e47'), ('#dbe6cc', '#3E5A3F')],
    chip_f3_on='#6B7F67', total_b='#1E3A2E', kv_bg='#ecefdf', kv_border='#d3d7c4', kv_small='#4a5646', kv_b='#1E3A2E',
    margin_bg='#e0f0e2', margin_border='#bcd8c1', margin_b='#1d5a35', terms_fg='#2e4a35', terms_border='#c7cbb8', grp_bg='#e4e9d8',
    snack_bg='#1E3A2E', snack_fg='#e9ebdf',
    login='radial-gradient(circle at 15% 15%, rgba(255, 226, 200, .55) 0, transparent 40%), radial-gradient(circle at 85% 20%, rgba(199, 203, 184, .9) 0, transparent 42%), radial-gradient(circle at 70% 90%, rgba(107, 127, 103, .25) 0, transparent 45%), #e9ebdf',
    login_shadow='0 10px 40px rgba(15, 31, 23, .18)',
)
F_NIGHT = dict(NIGHT)
F_NIGHT.update(
    vars='''  --primary: #9db48a; --on-primary: #0F1F17; --primary-container: #3E5A3F; --on-primary-container: #C7CBB8;
  --secondary-container: #2a4a38;
  --surface: #1E3A2E; --surface-1: #244235; --surface-2: #2b4c3d; --surface-3: #345a48;
  --background: #0F1F17;
  --on-surface: #e3e6d6; --on-surface-var: #aeb6a0; --outline: #6B7F67; --outline-var: #355244;
  --error: #ff9db6; --error-container: #4a2230; --warn: #ffd36b; --warn-container: #3f3515; --ok: #8fe0b0; --ok-container: #1c4a36;
  --c-blue: #9cc3e8; --c-purple: #9db48a; --c-pink: #ff9db6; --c-orange: #ffd36b; --c-yellow: #ffd36b;
  --c-green: #8fe0b0; --c-teal: #7fd4c0; --c-red: #ff7a8f;
  --bar: #0F1F17; --bar-ink: #C7CBB8;
  --grad: linear-gradient(#0F1F17, #0F1F17);
  --shadow-1: 0 1px 2px rgba(0, 0, 0, .6), 0 1px 3px 1px rgba(0, 0, 0, .4);
  --shadow-2: 0 1px 2px rgba(0, 0, 0, .6), 0 2px 8px 2px rgba(0, 0, 0, .45);''',
    body='radial-gradient(620px 420px at 100% 64px, rgba(107, 127, 103, .38), transparent 70%), radial-gradient(640px 420px at 0% 520px, rgba(62, 90, 63, .26), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(157, 180, 138, .10), transparent 70%), var(--background)',
    logo_bg='#C7CBB8', logo_fg='#1E3A2E', btn_hover='#b3c7a2', outlined_border='#4f6d51',
    th_bg='#244235', th_fg='#C7CBB8', th_border='#3E5A3F', tr_hover='#244235', mods_on='#1f4a36',
    stepbar='linear-gradient(90deg, #3E5A3F, #1E3A2E)', chip_fg='#e3e6d6', chip_blue_fg='#C7CBB8',
    f=[('rgba(120, 170, 230, .20)', '#a6c8ec'), ('rgba(70, 220, 150, .17)', '#8fe0b0'), ('rgba(255, 196, 60, .18)', '#ffd36b'), ('rgba(199, 203, 184, .14)', '#C7CBB8'), ('rgba(255, 107, 143, .2)', '#ff9db6'), ('rgba(107, 127, 103, .35)', '#C7CBB8')],
    chip_f3_on='#9db48a', total_b='#C7CBB8', kv_bg='#244235', kv_border='#3E5A3F', kv_small='#aeb6a0', kv_b='#e3e6d6',
    margin_bg='#1c4a36', margin_border='#2e6f50', margin_b='#8fe0b0', terms_fg='#C7CBB8', terms_border='#3E5A3F', grp_bg='#2a4a38',
    nav_fg='#d5d9c6', nav_active_fg='#e3e6d6', snack_bg='#C7CBB8', snack_fg='#1E3A2E',
    login='radial-gradient(circle at 85% 12%, rgba(107, 127, 103, .5) 0, transparent 45%), radial-gradient(circle at 12% 90%, rgba(62, 90, 63, .3) 0, transparent 45%), #0F1F17',
    login_shadow='0 10px 40px rgba(0, 0, 0, .55)', login_logo_fg='#0F1F17', cgchip_fg='#0F1F17', check_fg='#0F1F17', chip_filter_on_fg='#0F1F17', step_active_fg='#0F1F17',
)

# ---------------- palette «Terra»: #1A120E  #3A2418  #6B4A2E  #A88462  #D5C4AE  #4A4A46 ----------------
T_DAY = dict(DAY)
T_DAY.update(
    vars='''  --primary: #6B4A2E; --on-primary: #fff; --primary-container: #eadbc6; --on-primary-container: #3A2418;
  --secondary-container: #f0e4d2;
  --surface: #fff; --surface-1: #faf5ee; --surface-2: #f2e9dd; --surface-3: #e6d9c6;
  --background: #efe6da;
  --on-surface: #1A120E; --on-surface-var: #5e4f43; --outline: #A88462; --outline-var: #e0d3c0;
  --error: #c9283f; --error-container: #ffdde6; --warn: #8a5600; --warn-container: #ffeabb; --ok: #0f6e4a; --ok-container: #d3f4e5;
  --c-blue: #3a7ab5; --c-purple: #6B4A2E; --c-pink: #d6486f; --c-orange: #e08a00; --c-yellow: #f2a516;
  --c-green: #1e9e6a; --c-teal: #5f8f7a; --c-red: #c9283f;
  --bar: #3A2418; --bar-ink: #3A2418;
  --grad: linear-gradient(#3A2418, #3A2418);
  --shadow-1: 0 1px 2px rgba(26, 18, 14, .18), 0 1px 3px 1px rgba(26, 18, 14, .10);
  --shadow-2: 0 1px 2px rgba(26, 18, 14, .20), 0 2px 8px 2px rgba(26, 18, 14, .12);''',
    body='radial-gradient(560px 360px at 100% 64px, rgba(213, 196, 174, .80), transparent 70%), radial-gradient(640px 420px at 0% 480px, rgba(168, 132, 98, .22), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(255, 226, 200, .35), transparent 70%), var(--background)',
    appbar_shadow='0 2px 8px rgba(26, 18, 14, .40)', logo_bg='#fff', logo_fg='#3A2418', card_shadow='0 1px 3px rgba(26, 18, 14, .10)', card_alt='#A88462',
    btn_hover='#55391f', tonal_bg='#eadbc6', tonal_fg='#5a3b22', tonal_hover='#e0cdb0', outlined_border='#c9ab86',
    disabled_bg='rgba(26, 18, 14, .12)', disabled_fg='rgba(26, 18, 14, .38)',
    th_bg='#f0e4d2', th_fg='#5a3b22', th_border='#d5c4ae', tr_hover='#f8f1e7', mods_on='#e4f1e0', appname='#1A120E',
    stepbar='linear-gradient(90deg, #d5c4ae, #f0e4d2)', chip_fg='#4A4A46', chip_blue_bg='#f0e4d2', chip_blue_fg='#5a3b22',
    f=[('#dde8f5', '#2a5a8f'), ('#d3f4e5', '#0f6e4a'), ('#ffeabb', '#8a5600'), ('#e6e4df', '#4A4A46'), ('#ffdde6', '#b01e47'), ('#ecdcc4', '#6B4A2E')],
    chip_f3_on='#7a776f', total_b='#3A2418', kv_bg='#f2e9dd', kv_border='#e0d3c0', kv_small='#5e4f43', kv_b='#3A2418',
    margin_bg='#e4f1e0', margin_border='#c3dcbd', margin_b='#1d5a35', terms_fg='#5a3b22', terms_border='#d5c4ae', grp_bg='#f0e4d2',
    snack_bg='#3A2418', snack_fg='#f2e9dd',
    login='radial-gradient(circle at 15% 15%, rgba(255, 226, 200, .6) 0, transparent 40%), radial-gradient(circle at 85% 20%, rgba(213, 196, 174, .95) 0, transparent 42%), radial-gradient(circle at 70% 90%, rgba(168, 132, 98, .28) 0, transparent 45%), #efe6da',
    login_shadow='0 10px 40px rgba(26, 18, 14, .20)',
)
T_NIGHT = dict(NIGHT)
T_NIGHT.update(
    vars='''  --primary: #c4a07a; --on-primary: #1A120E; --primary-container: #6B4A2E; --on-primary-container: #D5C4AE;
  --secondary-container: #4a3020;
  --surface: #3A2418; --surface-1: #432a1c; --surface-2: #4d3222; --surface-3: #5a3b29;
  --background: #1A120E;
  --on-surface: #efe6da; --on-surface-var: #c3b5a3; --outline: #8a6b4b; --outline-var: #5a4030;
  --error: #ff9db6; --error-container: #4d2230; --warn: #ffd36b; --warn-container: #44351a; --ok: #8fe0b0; --ok-container: #24432f;
  --c-blue: #9cc3e8; --c-purple: #c4a07a; --c-pink: #ff9db6; --c-orange: #ffd36b; --c-yellow: #ffd36b;
  --c-green: #8fe0b0; --c-teal: #7fd4c0; --c-red: #ff7a8f;
  --bar: #1A120E; --bar-ink: #D5C4AE;
  --grad: linear-gradient(#1A120E, #1A120E);
  --shadow-1: 0 1px 2px rgba(0, 0, 0, .6), 0 1px 3px 1px rgba(0, 0, 0, .4);
  --shadow-2: 0 1px 2px rgba(0, 0, 0, .6), 0 2px 8px 2px rgba(0, 0, 0, .45);''',
    body='radial-gradient(620px 420px at 100% 64px, rgba(168, 132, 98, .36), transparent 70%), radial-gradient(640px 420px at 0% 520px, rgba(107, 74, 46, .30), transparent 70%), radial-gradient(560px 360px at 70% 100%, rgba(196, 160, 122, .10), transparent 70%), var(--background)',
    logo_bg='#D5C4AE', logo_fg='#3A2418', btn_hover='#d4b490', tonal_bg='#5a3b29', tonal_fg='#ffd9b0', tonal_hover='#6b4a2e', outlined_border='#7a5a3c',
    th_bg='#432a1c', th_fg='#D5C4AE', th_border='#6B4A2E', tr_hover='#432a1c', mods_on='#24432f',
    stepbar='linear-gradient(90deg, #6B4A2E, #3A2418)', chip_fg='#efe6da', chip_blue_fg='#D5C4AE',
    f=[('rgba(120, 170, 230, .20)', '#a6c8ec'), ('rgba(70, 220, 150, .17)', '#8fe0b0'), ('rgba(255, 196, 60, .18)', '#ffd36b'), ('rgba(213, 196, 174, .14)', '#D5C4AE'), ('rgba(255, 107, 143, .2)', '#ff9db6'), ('rgba(168, 132, 98, .32)', '#D5C4AE')],
    chip_f3_on='#c4a07a', total_b='#D5C4AE', kv_bg='#432a1c', kv_border='#6B4A2E', kv_small='#c3b5a3', kv_b='#efe6da',
    margin_bg='#24432f', margin_border='#356b48', margin_b='#8fe0b0', terms_fg='#D5C4AE', terms_border='#6B4A2E', grp_bg='#4a3020',
    nav_fg='#e0d3c0', nav_active_fg='#efe6da', snack_bg='#D5C4AE', snack_fg='#3A2418',
    login='radial-gradient(circle at 85% 12%, rgba(168, 132, 98, .45) 0, transparent 45%), radial-gradient(circle at 12% 90%, rgba(107, 74, 46, .35) 0, transparent 45%), #1A120E',
    login_shadow='0 10px 40px rgba(0, 0, 0, .55)', login_logo_fg='#1A120E', cgchip_fg='#1A120E', check_fg='#1A120E', chip_filter_on_fg='#1A120E', step_active_fg='#1A120E',
)

# ---------------- palette «Darth Vader»: nero, grigio antracite e blu elettrico (ispirata alle videate UniFi giorno / notte) ----------------
#   #0B0C0E  #15161A  #2A2C33  #9EA3AD  #4A93FF  #006FFF
V_DAY = dict(DAY)
V_DAY.update(
    vars='''  --primary: #006FFF; --on-primary: #fff; --primary-container: #dbe8ff; --on-primary-container: #0b2a5c;
  --secondary-container: #e8f0ff;
  --surface: #fff; --surface-1: #f6f7f9; --surface-2: #eef0f3; --surface-3: #e3e6eb;
  --background: #f4f5f7;
  --on-surface: #1b1c1f; --on-surface-var: #5b5f68; --outline: #8a8f99; --outline-var: #dfe1e6;
  --error: #d92d20; --error-container: #ffe3e0; --warn: #8a5600; --warn-container: #ffeabb; --ok: #12803d; --ok-container: #d6f5e1;
  --c-blue: #006FFF; --c-purple: #6a5fd8; --c-pink: #d6486f; --c-orange: #e08a00; --c-yellow: #f2a516;
  --c-green: #1fb157; --c-teal: #12a3a3; --c-red: #d92d20;
  --bar: #0B0C0E; --bar-ink: #fff;
  --grad: linear-gradient(#0B0C0E, #0B0C0E);
  --shadow-1: 0 1px 2px rgba(11, 12, 14, .16), 0 1px 3px 1px rgba(11, 12, 14, .08);
  --shadow-2: 0 1px 2px rgba(11, 12, 14, .18), 0 2px 8px 2px rgba(11, 12, 14, .10);''',
    body='linear-gradient(#f4f5f7, #f4f5f7)',
    appbar_shadow='0 2px 8px rgba(11, 12, 14, .40)', logo_bg='#fff', logo_fg='#0B0C0E', card_shadow='0 1px 3px rgba(11, 12, 14, .08)',
    card_alt='#1fb157', card_alt2='#0B0C0E',
    btn_hover='#0058cc', tonal_bg='#e8f0ff', tonal_fg='#0b3d91', tonal_hover='#d6e4ff', outlined_border='#9db9e6',
    disabled_bg='rgba(11, 12, 14, .12)', disabled_fg='rgba(11, 12, 14, .38)',
    th_bg='#eef0f3', th_fg='#2b2e35', th_border='#d3d6dd', tr_hover='#f4f7fc', mods_on='#e3f5ea', appname='#1b1c1f',
    stepbar='linear-gradient(90deg, #cfe0ff, #eef0f3)', chip_fg='#3d4048', chip_blue_bg='#e8f0ff', chip_blue_fg='#0b3d91',
    f=[('#dbe8ff', '#0b3d91'), ('#d6f5e1', '#12803d'), ('#ffeabb', '#8a5600'), ('#e8eaee', '#3d4048'), ('#ffe3e0', '#b42318'), ('#e0dcff', '#3b32a8')],
    chip_f3_on='#6b707a', total_b='#0B0C0E', kv_bg='#f1f3f6', kv_border='#dfe1e6', kv_small='#5b5f68', kv_b='#0B0C0E',
    margin_bg='#e3f5ea', margin_border='#bfe5cd', margin_b='#0d652d', terms_fg='#0b3d91', terms_border='#d3d6dd', grp_bg='#eef0f3',
    snack_bg='#15161A', snack_fg='#f2f3f5',
    login='radial-gradient(circle at 85% 20%, rgba(0, 111, 255, .18) 0, transparent 42%), radial-gradient(circle at 15% 90%, rgba(11, 12, 14, .10) 0, transparent 45%), #f4f5f7',
    login_shadow='0 10px 40px rgba(11, 12, 14, .18)', login_logo_bg='var(--bar)',
)
V_NIGHT = dict(NIGHT)
V_NIGHT.update(
    vars='''  --primary: #4a93ff; --on-primary: #04132e; --primary-container: #12315f; --on-primary-container: #cfe1ff;
  --secondary-container: #1b2233;
  --surface: #15161A; --surface-1: #1a1b20; --surface-2: #202127; --surface-3: #2a2c33;
  --background: #0B0C0E;
  --on-surface: #c6cad3; --on-surface-var: #9aa0ae; --outline: #5b606b; --outline-var: #2c2e35;
  --error: #ff8a80; --error-container: #4a1f1c; --warn: #ffd36b; --warn-container: #3f3515; --ok: #3ddc84; --ok-container: #123a26;
  --c-blue: #4a93ff; --c-purple: #a99dff; --c-pink: #ff9db6; --c-orange: #ffd36b; --c-yellow: #ffd36b;
  --c-green: #3ddc84; --c-teal: #5fe0d3; --c-red: #ff7a8f;
  --bar: #0B0C0E; --bar-ink: #c6cad3;
  --grad: linear-gradient(#0B0C0E, #0B0C0E);
  --shadow-1: 0 1px 2px rgba(0, 0, 0, .6), 0 1px 3px 1px rgba(0, 0, 0, .4);
  --shadow-2: 0 1px 2px rgba(0, 0, 0, .6), 0 2px 8px 2px rgba(0, 0, 0, .45);''',
    body='linear-gradient(#0B0C0E, #0B0C0E)',
    logo_bg='#f2f3f5', logo_fg='#0B0C0E', btn_hover='#7ab0ff', tonal_bg='#1d2a44', tonal_fg='#bcd5ff', tonal_hover='#25365a', outlined_border='#35507e',
    th_bg='#1a1b20', th_fg='#c9ccd3', th_border='#2c2e35', tr_hover='#1a1b20', mods_on='#123a26',
    stepbar='linear-gradient(90deg, #12315f, #202127)', chip_fg='#c6cad3', chip_blue_fg='#cfe1ff',
    f=[('rgba(74, 147, 255, .20)', '#9dc3ff'), ('rgba(61, 220, 132, .16)', '#3ddc84'), ('rgba(255, 196, 60, .18)', '#ffd36b'), ('rgba(158, 163, 173, .16)', '#c9ccd3'), ('rgba(255, 107, 143, .2)', '#ff9db6'), ('rgba(169, 157, 255, .22)', '#c9c2ff')],
    chip_f3_on='#9EA3AD', total_b='#c6cad3', kv_bg='#1a1b20', kv_border='#2c2e35', kv_small='#9aa0ae', kv_b='#c6cad3',
    margin_bg='#123a26', margin_border='#1f6a45', margin_b='#3ddc84', terms_fg='#9dc3ff', terms_border='#2c2e35', grp_bg='#202127',
    nav_fg='#c6cad3', nav_active_fg='#cfe1ff', snack_bg='#c6cad3', snack_fg='#15161A',
    login='radial-gradient(circle at 85% 12%, rgba(0, 111, 255, .28) 0, transparent 45%), radial-gradient(circle at 12% 90%, rgba(74, 147, 255, .10) 0, transparent 45%), #0B0C0E',
    login_shadow='0 10px 40px rgba(0, 0, 0, .6)', login_logo_fg='#04132e', cgchip_fg='#04132e', check_fg='#04132e', chip_filter_on_fg='#04132e', step_active_fg='#04132e',
)

# ---------------- palette «Christmas»: rosso Natale, verde abete e oro (con neve in movimento, vedi layout.html) ----------------
#   #07120C  #12452C  #1F7A3F  #B3202A  #FF6B6B  #FFD36B
X_DAY = dict(DAY)
X_DAY.update(
    vars='''  --primary: #B3202A; --on-primary: #fff; --primary-container: #fde0df; --on-primary-container: #5c0d12;
  --secondary-container: #e1f0e5;
  --surface: #fff; --surface-1: #fff7f4; --surface-2: #fbeeea; --surface-3: #f4dcd6;
  --background: #f6f3ec;
  --on-surface: #1d2a22; --on-surface-var: #5b6b60; --outline: #8a9a8f; --outline-var: #e2d9cf;
  --error: #c9283f; --error-container: #ffdde6; --warn: #8a5600; --warn-container: #ffeabb; --ok: #1f7a3f; --ok-container: #d6f0dd;
  --c-blue: #3f7fbf; --c-purple: #8a4bbf; --c-pink: #d6486f; --c-orange: #e08a00; --c-yellow: #d9a321;
  --c-green: #1f7a3f; --c-teal: #2a9d8f; --c-red: #B3202A;
  --bar: #12452C; --bar-ink: #fff;
  --grad: linear-gradient(#12452C, #12452C);
  --shadow-1: 0 1px 2px rgba(18, 69, 44, .18), 0 1px 3px 1px rgba(18, 69, 44, .10);
  --shadow-2: 0 1px 2px rgba(18, 69, 44, .20), 0 2px 8px 2px rgba(18, 69, 44, .12);''',
    body='radial-gradient(560px 360px at 100% 64px, rgba(179, 32, 42, .10), transparent 70%), radial-gradient(640px 420px at 0% 480px, rgba(31, 122, 63, .12), transparent 70%), linear-gradient(#f6f3ec, #f6f3ec)',
    appbar_shadow='0 2px 8px rgba(7, 18, 12, .40)', logo_bg='#fff', logo_fg='#B3202A', card_shadow='0 1px 3px rgba(18, 69, 44, .10)',
    card_alt='#1F7A3F', card_alt2='#C9962B',
    btn_hover='#8f1822', tonal_bg='#e1f0e5', tonal_fg='#14532d', tonal_hover='#cfe6d6', outlined_border='#d9a3a6',
    disabled_bg='rgba(29, 42, 34, .12)', disabled_fg='rgba(29, 42, 34, .38)',
    th_bg='#f7e9e4', th_fg='#7a1a21', th_border='#e8cfc8', tr_hover='#fdf5f2', mods_on='#e1f0e5', appname='#1d2a22',
    stepbar='linear-gradient(90deg, #f4c9c6, #e1f0e5)', chip_fg='#4b5a50', chip_blue_bg='#fde0df', chip_blue_fg='#7a1a21',
    f=[('#dbe8f7', '#25507f'), ('#d6f0dd', '#14532d'), ('#fff0c2', '#7a5200'), ('#ece8e0', '#4b5a50'), ('#ffdde6', '#b01e47'), ('#fde0df', '#7a1a21')],
    chip_f3_on='#7a8a7f', total_b='#12452C', kv_bg='#fbf1ee', kv_border='#ead9d2', kv_small='#5b6b60', kv_b='#12452C',
    margin_bg='#e1f0e5', margin_border='#b9d9c3', margin_b='#14532d', terms_fg='#7a1a21', terms_border='#e8cfc8', grp_bg='#f7e9e4',
    snack_bg='#12452C', snack_fg='#fff7f4',
    login='radial-gradient(circle at 15% 15%, rgba(179, 32, 42, .16) 0, transparent 40%), radial-gradient(circle at 85% 20%, rgba(31, 122, 63, .22) 0, transparent 42%), radial-gradient(circle at 70% 90%, rgba(217, 163, 33, .20) 0, transparent 45%), #f6f3ec',
    login_shadow='0 10px 40px rgba(18, 69, 44, .20)', login_logo_bg='var(--bar)',
)
X_NIGHT = dict(NIGHT)
X_NIGHT.update(
    vars='''  --primary: #ff6b6b; --on-primary: #3a0508; --primary-container: #6e1c22; --on-primary-container: #ffd9d6;
  --secondary-container: #1f4030;
  --surface: #12291D; --surface-1: #163223; --surface-2: #1c3b2b; --surface-3: #244a36;
  --background: #0A1A12;
  --on-surface: #e8f0ea; --on-surface-var: #a9c2b3; --outline: #5f8a70; --outline-var: #26452f;
  --error: #ff9db6; --error-container: #4d1d2c; --warn: #ffd36b; --warn-container: #3f3515; --ok: #7be0a0; --ok-container: #134a2c;
  --c-blue: #9cc3e8; --c-purple: #c9a6f0; --c-pink: #ff9db6; --c-orange: #ffb35c; --c-yellow: #ffd36b;
  --c-green: #7be0a0; --c-teal: #5fe0d3; --c-red: #ff7a8f;
  --bar: #07120C; --bar-ink: #e8f0ea;
  --grad: linear-gradient(#07120C, #07120C);
  --shadow-1: 0 1px 2px rgba(0, 0, 0, .6), 0 1px 3px 1px rgba(0, 0, 0, .4);
  --shadow-2: 0 1px 2px rgba(0, 0, 0, .6), 0 2px 8px 2px rgba(0, 0, 0, .45);''',
    body='radial-gradient(620px 420px at 100% 64px, rgba(179, 32, 42, .22), transparent 70%), radial-gradient(640px 420px at 0% 520px, rgba(31, 122, 63, .22), transparent 70%), linear-gradient(#0A1A12, #0A1A12)',
    logo_bg='#e8f0ea', logo_fg='#B3202A', btn_hover='#ff8c8c', tonal_bg='#4a3a14', tonal_fg='#ffe29a', tonal_hover='#5a4719', outlined_border='#7a3a40',
    th_bg='#163223', th_fg='#ffd9d6', th_border='#2c5a40', tr_hover='#163223', mods_on='#134a2c',
    stepbar='linear-gradient(90deg, #6e1c22, #1f4030)', chip_fg='#dcebe1', chip_blue_fg='#ffd9d6',
    f=[('rgba(156, 195, 232, .20)', '#b5d3f0'), ('rgba(123, 224, 160, .16)', '#7be0a0'), ('rgba(255, 211, 107, .18)', '#ffd36b'), ('rgba(169, 194, 179, .16)', '#cfe0d5'), ('rgba(255, 107, 143, .2)', '#ff9db6'), ('rgba(255, 107, 107, .2)', '#ffb0b0')],
    chip_f3_on='#a9c2b3', total_b='#ffd36b', kv_bg='#163223', kv_border='#2c5a40', kv_small='#a9c2b3', kv_b='#e8f0ea',
    margin_bg='#134a2c', margin_border='#1f6f44', margin_b='#7be0a0', terms_fg='#ffb0b0', terms_border='#2c5a40', grp_bg='#1c3b2b',
    nav_fg='#d5e4da', nav_active_fg='#ffd9d6', snack_bg='#e8f0ea', snack_fg='#12291D',
    login='radial-gradient(circle at 85% 12%, rgba(179, 32, 42, .40) 0, transparent 45%), radial-gradient(circle at 12% 90%, rgba(31, 122, 63, .35) 0, transparent 45%), #0A1A12',
    login_shadow='0 10px 40px rgba(0, 0, 0, .6)', login_logo_fg='#B3202A', cgchip_fg='#3a0508', check_fg='#3a0508', chip_filter_on_fg='#3a0508', step_active_fg='#3a0508',
)

out = '''/* =====================================================================
   PALETTE «VIOLA», «FORESTA» e «TERRA» (giorno e notte) - generato da tools/gen_palette.py: non modificare a mano.
   Ametista (id viola):   #0B0720  #25134D  #6A2FC8  #9D71E8  #D1B8FF  #F2E9FF
   Foresta (id foresta):  #0F1F17  #1E3A2E  #3E5A3F  #6B7F67  #C7CBB8  #3D4337
   Terra (id terra):      #1A120E  #3A2418  #6B4A2E  #A88462  #D5C4AE  #4A4A46
   Christmas (id christmas): #07120C  #12452C  #1F7A3F  #B3202A  #FF6B6B  #FFD36B
   Darth Vader (id vader): #0B0C0E  #15161A  #2A2C33  #9EA3AD  #4A93FF  #006FFF
   Si attiva con <html data-palette="viola"> o "foresta"; senza l'attributo resta il tema predefinito (verde acqua).
   La modalita' giorno / notte e' scelta con data-theme (light / dark) come per la palette predefinita.
   ===================================================================== */
'''
for nm, d, n in (('viola', DAY, NIGHT), ('foresta', F_DAY, F_NIGHT), ('terra', T_DAY, T_NIGHT), ('vader', V_DAY, V_NIGHT), ('christmas', X_DAY, X_NIGHT)):
    out += '\n/* ---------- %s: giorno ---------- */\n' % nm + block(d, nm, False) + '\n\n/* ---------- %s: notte ---------- */\n' % nm + block(n, nm, True) + '\n'
out += '''
/* selettore palette nel menu utente */
.palette-sec { padding: 6px 16px 2px; font-size: 12px; color: var(--on-surface-var); text-transform: uppercase; letter-spacing: .04em; }
.menu button[data-palette-set] .sw { display: inline-flex; width: 28px; height: 16px; border-radius: 8px; overflow: hidden; flex: none; box-shadow: 0 0 0 1px var(--outline-var); }
.menu button[data-palette-set] .sw i { flex: 1; display: block; }
.menu button[data-palette-set] .tick { margin-left: auto; visibility: hidden; }
.menu button[data-palette-set].on .tick { visibility: visible; color: var(--primary); }
'''
dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'static', 'css', 'palettes.css')
open(dest, 'w', encoding='utf8').write(out)
print('scritto', os.path.normpath(dest), len(out), 'byte')
