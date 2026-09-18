
const ESPECIALIDADES = ['Cardiologia','Dermatologia','Pediatria','Ortopedia','Clínico Geral','Ginecologia'];
const CONVENIOS = ['Unimed','Amil','Bradesco','Particular'];
const WEEKDAY_LABELS = ['dom','seg','ter','qua','qui','sex','sáb'];
const pad = n => String(n).padStart(2,'0');

function dateStrOffset(offset){
  const d = new Date();
  d.setHours(0,0,0,0);
  d.setDate(d.getDate()+offset);
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
}
function dayLabel(dateStr){ return dateStr.slice(8,10); }
function weekdayOf(dateStr){ return new Date(dateStr+'T00:00:00').getDay(); }
function fmtDateLabel(dateStr){
  const d = new Date(dateStr+'T00:00:00');
  return `${pad(d.getDate())}/${pad(d.getMonth()+1)}`;
}

const BASE_DOCTORS = [
  { id:'d1', nome:'Dra. Ana Ferreira', especialidade:'Cardiologia', cidade:'São Paulo, SP', convenios:['Unimed','Bradesco'], iniciais:'AF', diasAtendimento:[1,2,3,4,5], horarios:[{ini:'08:00',fim:'12:00'},{ini:'14:00',fim:'18:00'}], bookedOffsets:{2:['09:00','09:15'],3:['15:00']} },
  { id:'d2', nome:'Dr. Bruno Castro', especialidade:'Dermatologia', cidade:'São Paulo, SP', convenios:['Amil'], iniciais:'BC', diasAtendimento:[1,3,5], horarios:[{ini:'09:00',fim:'13:00'}], bookedOffsets:{4:['10:00']} },
  { id:'d3', nome:'Dra. Carla Souza', especialidade:'Pediatria', cidade:'Campinas, SP', convenios:['Unimed','Amil','Bradesco'], iniciais:'CS', diasAtendimento:[1,2,3,4,5,6], horarios:[{ini:'08:00',fim:'12:00'}], bookedOffsets:{} },
  { id:'d4', nome:'Dr. Diego Martins', especialidade:'Ortopedia', cidade:'São Paulo, SP', convenios:['Bradesco'], iniciais:'DM', diasAtendimento:[2,4], horarios:[{ini:'13:00',fim:'17:00'}], bookedOffsets:{} },
  { id:'d5', nome:'Dra. Elisa Rocha', especialidade:'Clínico Geral', cidade:'Santos, SP', convenios:['Unimed'], iniciais:'ER', diasAtendimento:[1,2,3,4,5], horarios:[{ini:'07:30',fim:'11:30'},{ini:'13:00',fim:'16:00'}], bookedOffsets:{2:['08:00']} },
];

class Component extends DCLogic {
  constructor(props){
    super(props);
    const appt1Date = new Date(Date.now() + 4*24*3600*1000);
    const appt2Date = new Date(Date.now() + 15*3600*1000);
    const mk = (dt) => `${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}`;
    const roundTime = (dt) => { const m = dt.getMinutes(); const rounded = Math.round(m/15)*15; return `${pad(dt.getHours())}:${pad(rounded%60)}`; };
    this.state = {
      role: (props.papelInicial === 'medico') ? 'medico' : 'paciente',
      screen: 'busca',
      medicoScreen: 'cadastro',
      filtroEsp: '',
      filtroLocal: '',
      selectedDoctorId: null,
      selectedDateStr: null,
      selectedSlotTime: null,
      rescheduleApptId: null,
      bookedOverrides: {},
      appointments: [
        { id:'a1', doctorId:'d1', dateStr: mk(appt1Date), timeStr: roundTime(appt1Date), cancelConfirming:false },
        { id:'a2', doctorId:'d2', dateStr: mk(appt2Date), timeStr: roundTime(appt2Date), cancelConfirming:false },
      ],
      medNome:'', medEspecialidade:'', medConvenios:[], medDias:[], medHoraIni:'08:00', medHoraFim:'18:00',
      medCadastrado:false,
      doctorAppointments:[],
      loggedInPaciente:false, loggedInMedico:false, authScreen:'login', loginError:null,
      loginEmail:'', loginSenha:'',
      recSenhaEmail:'', recSenhaEnviado:false,
      pacNome:'', pacEmail:'', pacSenha:'',
      medEmail:'', medSenha:'',
    };
  }

  needsAuth(){ return this.state.role==='paciente' ? !this.state.loggedInPaciente : !this.state.loggedInMedico; }

  onLogin(){
    if (this.state.role==='paciente'){ this.setState({ loggedInPaciente:true, screen:'busca', loginError:null }); }
    else if (this.state.medCadastrado){ this.setState({ loggedInMedico:true, screen:'agenda', loginError:null }); }
    else { this.setState({ loginError:'Nenhum cadastro encontrado para este médico. Crie sua conta.' }); }
  }
  enviarRecuperacao(){ this.setState({ recSenhaEnviado:true }); }
  criarContaPaciente(){
    if (!this.state.pacNome.trim() || !this.state.pacEmail.trim() || !this.state.pacSenha.trim()) return;
    this.setState({ loggedInPaciente:true, role:'paciente', screen:'busca', authScreen:'login' });
  }

  antecHoras(){ return this.props.antecedenciaMinimaHoras ?? 48; }
  tolHoras(){ return this.props.toleranciaCancelamentoHoras ?? 24; }

  allDoctors(){
    const list = [...BASE_DOCTORS];
    if (this.state.medCadastrado) {
      list.push({ id:'meu', nome: this.state.medNome || 'Meu Perfil', especialidade: this.state.medEspecialidade, cidade:'São Paulo, SP', convenios: this.state.medConvenios, iniciais:(this.state.medNome||'EU').split(' ').map(w=>w[0]).slice(0,2).join('').toUpperCase(), diasAtendimento: this.state.medDias, horarios:[{ini:this.state.medHoraIni,fim:this.state.medHoraFim}], bookedOffsets:{} });
    }
    return list;
  }
  findDoctor(id){ return this.allDoctors().find(d=>d.id===id); }

  setRole(role){
    const authed = role==='paciente' ? this.state.loggedInPaciente : this.state.loggedInMedico;
    this.setState({ role, screen: authed ? (role==='paciente'?'busca':'agenda') : 'busca', authScreen:'login', loginError:null });
  }

  selectDoctor(id){ this.setState({ selectedDoctorId:id, screen:'detalhe', selectedDateStr:null, selectedSlotTime:null }); }

  isSlotBookable(dt){ return dt.getTime() - Date.now() >= this.antecHoras()*3600*1000; }
  canCancel(dt){ return dt.getTime() - Date.now() >= this.tolHoras()*3600*1000; }

  bookedFor(docId, dateStr){
    const base = (this.findDoctor(docId)?.bookedOffsets)||{};
    const dOff = Math.round((new Date(dateStr+'T00:00:00') - new Date(dateStrOffset(0)+'T00:00:00'))/86400000);
    const overrides = (this.state.bookedOverrides[docId]||{})[dateStr] || [];
    return [...(base[dOff]||[]), ...overrides];
  }

  generateSlots(doc, dateStr){
    if (!dateStr) return [];
    const slots = [];
    (doc.horarios||[]).forEach(b=>{
      let [h,m] = b.ini.split(':').map(Number);
      const [eh,em] = b.fim.split(':').map(Number);
      const bookedList = this.bookedFor(doc.id, dateStr);
      while (h < eh || (h===eh && m<em)){
        const timeStr = `${pad(h)}:${pad(m)}`;
        const dt = new Date(dateStr+'T'+timeStr+':00');
        const bookable = this.isSlotBookable(dt);
        const taken = bookedList.includes(timeStr);
        slots.push({ time: timeStr, disabled: !bookable || taken, reason: taken ? 'Ocupado' : (!bookable ? `Antecedência mín. ${this.antecHoras()}h` : null) });
        m += 15; if (m>=60){ m-=60; h+=1; }
      }
    });
    return slots;
  }

  confirmarAgendamento(){
    const { selectedDoctorId, selectedDateStr, selectedSlotTime, rescheduleApptId } = this.state;
    if (!selectedDoctorId || !selectedDateStr || !selectedSlotTime) return;
    const overrides = JSON.parse(JSON.stringify(this.state.bookedOverrides));
    overrides[selectedDoctorId] = overrides[selectedDoctorId] || {};
    overrides[selectedDoctorId][selectedDateStr] = [...(overrides[selectedDoctorId][selectedDateStr]||[]), selectedSlotTime];

    let appointments = [...this.state.appointments];
    if (rescheduleApptId){
      appointments = appointments.map(a => a.id===rescheduleApptId ? { ...a, dateStr: selectedDateStr, timeStr: selectedSlotTime, cancelConfirming:false } : a);
    } else {
      appointments.push({ id: 'a'+Date.now(), doctorId: selectedDoctorId, dateStr: selectedDateStr, timeStr: selectedSlotTime, cancelConfirming:false });
    }
    this.setState({ bookedOverrides: overrides, appointments, screen:'confirmacao', rescheduleApptId:null });
  }

  cancelarConsulta(id){
    this.setState({ appointments: this.state.appointments.filter(a=>a.id!==id) });
  }
  toggleCancelConfirm(id, val){
    this.setState({ appointments: this.state.appointments.map(a=>a.id===id?{...a,cancelConfirming:val}:a) });
  }
  reagendarStart(appt){
    this.setState({ selectedDoctorId: appt.doctorId, screen:'detalhe', selectedDateStr:null, selectedSlotTime:null, rescheduleApptId: appt.id });
  }

  toggleConvenio(c){
    const has = this.state.medConvenios.includes(c);
    this.setState({ medConvenios: has ? this.state.medConvenios.filter(x=>x!==c) : [...this.state.medConvenios, c] });
  }
  toggleDia(idx){
    const has = this.state.medDias.includes(idx);
    this.setState({ medDias: has ? this.state.medDias.filter(x=>x!==idx) : [...this.state.medDias, idx] });
  }
  criarPerfil(){
    const nomes = ['Marcos Lima','Beatriz Nunes','Carla Prado','Rafael Dias'];
    const now = Date.now();
    const doctorAppointments = nomes.slice(0,3).map((paciente,i) => {
      const dt = new Date(now + (2+i*2)*24*3600*1000);
      return { paciente, dataLabel: fmtDateLabel(`${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}`), timeStr: ['09:00','10:30','14:00'][i], convenio: this.state.medConvenios[i % Math.max(this.state.medConvenios.length,1)] || CONVENIOS[0] };
    });
    this.setState({ medCadastrado:true, loggedInMedico:true, screen:'agenda', doctorAppointments, authScreen:'login' });
  }

  renderVals(){
    const s = this.state;
    const pillBase = { padding:'9px 18px', borderRadius:100, border:'none', fontSize:13.5, fontWeight:600, cursor:'pointer', transition:'background .15s' };
    const tabStylePaciente = { ...pillBase, background: s.role==='paciente' ? '#3B6FE0':'transparent', color: s.role==='paciente' ? '#fff':'oklch(0.4 0.02 75)' };
    const tabStyleMedico = { ...pillBase, background: s.role==='medico' ? '#3B6FE0':'transparent', color: s.role==='medico' ? '#fff':'oklch(0.4 0.02 75)' };

    const isPaciente = s.role==='paciente';
    const needsAuth = this.needsAuth();
    const isLogin = needsAuth && s.authScreen==='login';
    const isRecuperar = needsAuth && s.authScreen==='recuperar';
    const isEscolha = needsAuth && s.authScreen==='escolha';
    const isCadastroPacienteAuth = needsAuth && s.authScreen==='cadastroPaciente';
    const isBusca = !needsAuth && isPaciente && s.screen==='busca';
    const isDetalhe = !needsAuth && isPaciente && s.screen==='detalhe';
    const isConfirmacao = !needsAuth && isPaciente && s.screen==='confirmacao';
    const isMinhas = !needsAuth && isPaciente && s.screen==='minhas';
    const isCadastroMedico = needsAuth && !isPaciente && s.authScreen==='cadastroMedico';
    const isAgendaMedico = !needsAuth && !isPaciente && s.screen==='agenda';

    let headerTitle = 'Agende', headerSubtitle = null, showBack = false, onBack = ()=>{};
    if (isLogin){ headerTitle='Entrar'; headerSubtitle = isPaciente ? 'Acesse sua conta de paciente' : 'Acesse sua conta de médico'; }
    if (isRecuperar){ headerTitle='Recuperar senha'; showBack=true; onBack=()=>this.setState({authScreen:'login', recSenhaEnviado:false}); }
    if (isEscolha){ headerTitle='Criar conta'; showBack=true; onBack=()=>this.setState({authScreen:'login'}); }
    if (isCadastroPacienteAuth){ headerTitle='Cadastro de paciente'; showBack=true; onBack=()=>this.setState({authScreen:'escolha'}); }
    if (isCadastroMedico){ headerTitle='Cadastro de médico'; headerSubtitle='Autocadastro, sem validação de CRM'; showBack=true; onBack=()=>this.setState({authScreen:'escolha'}); }
    if (isBusca){ headerTitle='Agende'; headerSubtitle='Encontre um médico e agende'; }
    if (isDetalhe){ headerTitle = this.state.rescheduleApptId ? 'Reagendar consulta' : 'Escolher horário'; showBack=true; onBack=()=>this.setState({screen: this.state.rescheduleApptId?'minhas':'busca', rescheduleApptId:null}); }
    if (isConfirmacao){ headerTitle='Confirmado'; }
    if (isMinhas){ headerTitle='Minhas consultas'; showBack=true; onBack=()=>this.setState({screen:'busca'}); }
    if (isAgendaMedico){ headerTitle='Minha agenda'; }

    const doctors = this.allDoctors().filter(d => (!s.filtroEsp || d.especialidade===s.filtroEsp) && (!s.filtroLocal || d.cidade.toLowerCase().includes(s.filtroLocal.toLowerCase())));
    const doctorList = doctors.map(d => ({ ...d, convenioChips:d.convenios, onSelect:()=>this.selectDoctor(d.id) }));

    const selectedDoctor = s.selectedDoctorId ? { ...this.findDoctor(s.selectedDoctorId) } : null;
    if (selectedDoctor) selectedDoctor.convenioChips = selectedDoctor.convenios;

    const dateOptions = selectedDoctor ? Array.from({length:10}).map((_,i)=>dateStrOffset(i)).filter(ds=>selectedDoctor.diasAtendimento.includes(weekdayOf(ds))).slice(0,6).map(ds => {
      const selected = ds === s.selectedDateStr;
      return {
        dateStr: ds, day: dayLabel(ds), weekday: WEEKDAY_LABELS[weekdayOf(ds)],
        onClick: ()=>this.setState({ selectedDateStr: ds, selectedSlotTime:null }),
        style: { flex:'none', width:52, padding:'8px 0', borderRadius:12, border: selected? '2px solid #3B6FE0':'1px solid oklch(0.88 0.01 75)', background: selected? 'oklch(0.94 0.05 250)':'#fff', color: selected? '#3B6FE0':'oklch(0.3 0.02 75)', cursor:'pointer', textAlign:'center' }
      };
    }) : [];

    const slotOptions = (selectedDoctor && s.selectedDateStr) ? this.generateSlots(selectedDoctor, s.selectedDateStr).map(sl => {
      const selected = sl.time === s.selectedSlotTime;
      return { ...sl, onClick: sl.disabled? undefined : ()=>this.setState({selectedSlotTime: sl.time}),
        style: { padding:'11px 0', borderRadius:10, textAlign:'center', fontSize:13.5, fontWeight:600, cursor: sl.disabled?'not-allowed':'pointer',
          border: selected? '2px solid #3B6FE0' : '1px solid oklch(0.88 0.01 75)',
          background: sl.disabled ? 'oklch(0.95 0.01 75)' : (selected ? 'oklch(0.94 0.05 250)' : '#fff'),
          color: sl.disabled ? 'oklch(0.7 0.01 75)' : (selected ? '#3B6FE0' : 'oklch(0.3 0.02 75)') } };
    }) : [];

    const confirmDisabled = !s.selectedDoctorId || !s.selectedDateStr || !s.selectedSlotTime;
    const confirmButtonStyle = { width:'100%', padding:14, borderRadius:12, border:'none', fontSize:14.5, fontWeight:700, cursor: confirmDisabled?'not-allowed':'pointer',
      background: confirmDisabled ? 'oklch(0.85 0.01 75)' : '#3B6FE0', color: confirmDisabled? 'oklch(0.55 0.01 75)':'#fff', boxShadow: confirmDisabled?'none':'0 6px 16px rgba(59,111,224,0.35)' };

    const lastAppt = s.appointments[s.appointments.length-1];
    const lastDoc = lastAppt ? this.findDoctor(lastAppt.doctorId) : null;
    const lastAppointment = lastAppt ? { doctorNome:lastDoc?.nome, especialidade:lastDoc?.especialidade, dataLabel: fmtDateLabel(lastAppt.dateStr), timeStr: lastAppt.timeStr, convenio: lastDoc?.convenios?.[0] } : {};

    const appointmentCards = [...s.appointments].sort((a,b)=> (a.dateStr+a.timeStr).localeCompare(b.dateStr+b.timeStr)).map(a => {
      const doc = this.findDoctor(a.doctorId);
      const dt = new Date(a.dateStr+'T'+a.timeStr+':00');
      const blocked = !this.canCancel(dt);
      return {
        id:a.id, doctorNome: doc?.nome, especialidade: doc?.especialidade, dataLabel: fmtDateLabel(a.dateStr), timeStr:a.timeStr, convenio: doc?.convenios?.[0],
        blocked, confirmingCancel: a.cancelConfirming,
        badgeLabel: blocked ? 'Bloqueada' : 'Confirmada',
        badgeStyle: { fontSize:11, fontWeight:700, padding:'4px 9px', borderRadius:100, color: blocked?'oklch(0.5 0.12 40)':'oklch(0.45 0.1 150)', background: blocked?'oklch(0.95 0.04 40)':'oklch(0.94 0.05 150)' },
        actionBtnStyle: { flex:1, padding:'9px 0', borderRadius:9, border:'1px solid #3B6FE0', background:'#fff', color: blocked?'oklch(0.75 0.01 75)':'#3B6FE0', fontSize:13, fontWeight:600, cursor: blocked?'not-allowed':'pointer', opacity: blocked?0.6:1 },
        cancelBtnStyle: { flex:1, padding:'9px 0', borderRadius:9, border:'1px solid oklch(0.85 0.05 25)', background:'#fff', color: blocked?'oklch(0.75 0.01 75)':'oklch(0.5 0.14 25)', fontSize:13, fontWeight:600, cursor: blocked?'not-allowed':'pointer', opacity: blocked?0.6:1 },
        onReagendar: blocked? undefined : ()=>this.reagendarStart(a),
        onCancelarClick: blocked? undefined : ()=>this.toggleCancelConfirm(a.id,true),
        onCancelarConfirm: ()=>this.cancelarConsulta(a.id),
        onCancelarAbort: ()=>this.toggleCancelConfirm(a.id,false),
      };
    });

    const chipStyleFor = (active) => ({ padding:'9px 14px', borderRadius:100, border: active?'2px solid #3B6FE0':'1px solid oklch(0.88 0.01 75)', background: active?'oklch(0.94 0.05 250)':'#fff', color: active?'#3B6FE0':'oklch(0.35 0.02 75)', fontSize:13, fontWeight:600, cursor:'pointer' });
    const convenioChoices = CONVENIOS.map(c => ({ label:c, onClick:()=>this.toggleConvenio(c), style: chipStyleFor(s.medConvenios.includes(c)) }));
    const diaChoices = [1,2,3,4,5].map(idx => ({ label: WEEKDAY_LABELS[idx], onClick:()=>this.toggleDia(idx), style: { ...chipStyleFor(s.medDias.includes(idx)), padding:'9px 12px' } }));
    const horaOptions = Array.from({length:12}).map((_,i)=>`${pad(7+i)}:00`);

    const cadastroDisabled = !s.medNome.trim() || !s.medEmail.trim() || !s.medSenha.trim() || !s.medEspecialidade || s.medConvenios.length===0 || s.medDias.length===0;
    const criarPerfilStyle = { width:'100%', padding:14, borderRadius:12, border:'none', fontSize:14.5, fontWeight:700, cursor: cadastroDisabled?'not-allowed':'pointer', background: cadastroDisabled?'oklch(0.85 0.01 75)':'#3B6FE0', color: cadastroDisabled?'oklch(0.55 0.01 75)':'#fff' };

    return {
      tabStylePaciente, tabStyleMedico,
      setRolePaciente: ()=>this.setRole('paciente'), setRoleMedico: ()=>this.setRole('medico'),
      showBack, onBack, headerTitle, headerSubtitle,
      isLogin, isRecuperar, isEscolha, isCadastroPacienteAuth,
      isBusca, isDetalhe, isConfirmacao, isMinhas, isCadastroMedico, isAgendaMedico,
      loginEmail: s.loginEmail, loginSenha: s.loginSenha, loginError: s.loginError,
      onLoginEmail: e=>this.setState({loginEmail:e.target.value}), onLoginSenha: e=>this.setState({loginSenha:e.target.value}),
      onEntrar: ()=>this.onLogin(),
      goRecuperar: ()=>this.setState({authScreen:'recuperar'}), goEscolha: ()=>this.setState({authScreen:'escolha'}),
      goLoginFromEscolha: ()=>this.setState({authScreen:'login'}),
      escolherPaciente: ()=>this.setState({role:'paciente', authScreen:'cadastroPaciente'}),
      escolherMedico: ()=>this.setState({role:'medico', authScreen:'cadastroMedico'}),
      recSenhaEmail: s.recSenhaEmail, recSenhaEnviado: s.recSenhaEnviado, recSenhaPendente: !s.recSenhaEnviado,
      onRecSenhaEmail: e=>this.setState({recSenhaEmail:e.target.value}), onEnviarRecuperacao: ()=>this.enviarRecuperacao(),
      voltarLogin: ()=>this.setState({authScreen:'login', recSenhaEnviado:false}),
      pacNome: s.pacNome, pacEmail: s.pacEmail, pacSenha: s.pacSenha,
      onPacNome: e=>this.setState({pacNome:e.target.value}), onPacEmail: e=>this.setState({pacEmail:e.target.value}), onPacSenha: e=>this.setState({pacSenha:e.target.value}),
      pacCadastroDisabled: !s.pacNome.trim() || !s.pacEmail.trim() || !s.pacSenha.trim(),
      onCriarContaPaciente: ()=>this.criarContaPaciente(),
      medEmail: s.medEmail, medSenha: s.medSenha,
      onMedEmail: e=>this.setState({medEmail:e.target.value}), onMedSenha: e=>this.setState({medSenha:e.target.value}),
      especialidadeOptions: ESPECIALIDADES,
      filtroEsp: s.filtroEsp, filtroLocal: s.filtroLocal,
      onFiltroEsp: e=>this.setState({filtroEsp:e.target.value}), onFiltroLocal: e=>this.setState({filtroLocal:e.target.value}),
      onUsarLocalizacao: ()=>this.setState({filtroLocal:'São Paulo, SP'}),
      doctorList, resultCountLabel: `${doctorList.length} médico(s) encontrado(s)`, noResults: doctorList.length===0,
      selectedDoctor, dateOptions, slotOptions, noSlots: !!(selectedDoctor && s.selectedDateStr) && slotOptions.length===0,
      antecedenciaLabel: this.antecHoras(), toleranciaLabel: this.tolHoras(),
      confirmDisabled, confirmButtonStyle, confirmButtonLabel: s.rescheduleApptId ? 'Confirmar novo horário' : 'Confirmar agendamento',
      onConfirmarAgendamento: ()=>this.confirmarAgendamento(),
      lastAppointment, goMinhas: ()=>this.setState({screen:'minhas'}), goBuscaFromConfirm: ()=>this.setState({screen:'busca'}),
      appointmentCards, noAppointments: appointmentCards.length===0, goBuscaFromMinhas: ()=>this.setState({screen:'busca'}),
      medNome: s.medNome, medEspecialidade: s.medEspecialidade, medConvenios: s.medConvenios,
      onMedNome: e=>this.setState({medNome:e.target.value}), onMedEspecialidade: e=>this.setState({medEspecialidade:e.target.value}),
      convenioChoices, diaChoices, horaOptions,
      medHoraIni: s.medHoraIni, medHoraFim: s.medHoraFim,
      onMedHoraIni: e=>this.setState({medHoraIni:e.target.value}), onMedHoraFim: e=>this.setState({medHoraFim:e.target.value}),
      cadastroDisabled, criarPerfilStyle, onCriarPerfil: ()=>this.criarPerfil(),
      doctorAppointmentCards: s.doctorAppointments, noDoctorAppointments: s.doctorAppointments.length===0,
    };
  }
}

