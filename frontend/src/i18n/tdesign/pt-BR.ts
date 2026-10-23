import enUS from 'tdesign-vue-next/esm/locale/en_US'

/**
 * TDesign 组件内置文案（巴西葡萄牙语）局部覆盖。
 *
 * 只覆盖本仓实际用到的组件；其余段落继承 en_US。
 * 注意：组件库默认语言是 zh_CN，所以必须整体以 en_US 为底，否则未覆盖的段落会变成中文。
 */
export default {
  ...enUS,
  pagination: {
    itemsPerPage: '{size} / página',
    jumpTo: 'Ir para',
    page: '',
    total: 'nenhum item | 1 item | {count} itens',
  },
  datePicker: {
    ...enUS.datePicker,
    placeholder: {
      date: 'selecionar data',
      month: 'selecionar mês',
      year: 'selecionar ano',
      quarter: 'selecionar trimestre',
      week: 'selecionar semana',
    },
    weekdays: ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'],
    months: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'],
    quarters: ['T1', 'T2', 'T3', 'T4'],
    confirm: 'Confirmar',
    selectTime: 'Selecionar hora',
    selectDate: 'Selecionar data',
    nextYear: 'Próximo ano',
    preYear: 'Ano anterior',
    nextMonth: 'Próximo mês',
    preMonth: 'Mês anterior',
    preDecade: 'Década anterior',
    nextDecade: 'Próxima década',
    now: 'Agora',
  },
  upload: {
    ...enUS.upload,
    sizeLimitMessage: 'O arquivo excede o limite de {sizeLimit}',
    cancelUploadText: 'Cancelar',
    triggerUploadText: {
      fileInput: 'Enviar',
      image: 'Clique para enviar',
      normal: 'Enviar',
      reupload: 'Enviar novamente',
      continueUpload: 'Continuar envio',
      delete: 'Excluir',
      uploading: 'Enviando',
    },
    dragger: {
      dragDropText: 'Solte aqui',
      draggingText: 'Arraste o arquivo para esta área para enviar',
      clickAndDragText: 'Clique em «Enviar» ou arraste o arquivo para esta área',
    },
    file: {
      fileNameText: 'nome',
      fileSizeText: 'tamanho',
      fileStatusText: 'status',
      fileOperationText: 'operação',
      fileOperationDateText: 'data',
    },
    progress: {
      uploadingText: 'Enviando',
      waitingText: 'Aguardando',
      failText: 'Falhou',
      successText: 'Concluído',
    },
  },
  table: {
    ...enUS.table,
    empty: 'Sem dados',
    loadingText: 'carregando…',
    loadingMoreText: 'carregando mais',
    sortAscendingOperationText: 'clique para ordenar crescente',
    sortDescendingOperationText: 'clique para ordenar decrescente',
    sortCancelOperationText: 'clique para cancelar a ordenação',
    clearFilterResultButtonText: 'Limpar',
    columnConfigButtonText: 'Configurar colunas',
    columnConfigTitleText: 'Configuração de colunas',
    columnConfigDescriptionText: 'Selecione as colunas que deseja exibir',
    confirmText: 'Confirmar',
    cancelText: 'Cancelar',
    resetText: 'Redefinir',
    selectAllText: 'Selecionar tudo',
    searchResultText:
      'Busca «{result}»: nenhum item. | Busca «{result}»: 1 item. | Busca «{result}»: {count} itens.',
  },
  select: {
    ...enUS.select,
    empty: 'Sem dados',
    loadingText: 'carregando…',
    placeholder: 'selecione',
  },
  empty: {
    ...enUS.empty,
    titleText: {
      maintenance: 'Em construção',
      success: 'Sucesso',
      fail: 'Falha',
      empty: 'Sem dados',
      networkError: 'Erro de rede',
    },
  },
  dialog: { confirm: 'Confirmar', cancel: 'Cancelar' },
  drawer: { confirm: 'Confirmar', cancel: 'Cancelar' },
  popconfirm: { confirm: { content: 'OK' }, cancel: { content: 'Cancelar' } },
  colorPicker: {
    ...enUS.colorPicker,
    swatchColorTitle: 'Padrão do sistema',
    recentColorTitle: 'Usadas recentemente',
    clearConfirmText: 'Limpar as cores usadas recentemente?',
    singleColor: 'Única',
    gradientColor: 'Gradiente',
  },
  input: { ...enUS.input, placeholder: 'digite um valor' },
}
