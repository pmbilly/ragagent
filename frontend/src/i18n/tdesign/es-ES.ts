import enUS from 'tdesign-vue-next/esm/locale/en_US'

/**
 * TDesign 组件内置文案（西班牙语）局部覆盖。
 *
 * 只覆盖本仓实际用到的组件；其余段落继承 en_US。
 * 注意：组件库默认语言是 zh_CN（mergeWith(defaultGlobalConfig, globalConfig)），
 * 所以这里必须整体以 en_US 为底，不能只导出局部对象，否则未覆盖的段落会变成中文。
 */
export default {
  ...enUS,
  pagination: {
    itemsPerPage: '{size} / página',
    jumpTo: 'Ir a',
    page: '',
    total: 'sin elementos | 1 elemento | {count} elementos',
  },
  datePicker: {
    ...enUS.datePicker,
    placeholder: {
      date: 'seleccionar fecha',
      month: 'seleccionar mes',
      year: 'seleccionar año',
      quarter: 'seleccionar trimestre',
      week: 'seleccionar semana',
    },
    weekdays: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
    months: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'],
    quarters: ['T1', 'T2', 'T3', 'T4'],
    confirm: 'Confirmar',
    selectTime: 'Seleccionar hora',
    selectDate: 'Seleccionar fecha',
    nextYear: 'Año siguiente',
    preYear: 'Año anterior',
    nextMonth: 'Mes siguiente',
    preMonth: 'Mes anterior',
    preDecade: 'Década anterior',
    nextDecade: 'Década siguiente',
    now: 'Ahora',
  },
  upload: {
    ...enUS.upload,
    sizeLimitMessage: 'El archivo supera el límite de {sizeLimit}',
    cancelUploadText: 'Cancelar',
    triggerUploadText: {
      fileInput: 'Subir',
      image: 'Haz clic para subir',
      normal: 'Subir',
      reupload: 'Volver a subir',
      continueUpload: 'Continuar subida',
      delete: 'Eliminar',
      uploading: 'Subiendo',
    },
    dragger: {
      dragDropText: 'Suelta aquí',
      draggingText: 'Arrastra el archivo a esta zona para subirlo',
      clickAndDragText: 'Haz clic en «Subir» o arrastra el archivo a esta zona',
    },
    file: {
      fileNameText: 'nombre',
      fileSizeText: 'tamaño',
      fileStatusText: 'estado',
      fileOperationText: 'operación',
      fileOperationDateText: 'fecha',
    },
    progress: {
      uploadingText: 'Subiendo',
      waitingText: 'En espera',
      failText: 'Error',
      successText: 'Correcto',
    },
  },
  table: {
    ...enUS.table,
    empty: 'Sin datos',
    loadingText: 'cargando…',
    loadingMoreText: 'cargando más',
    sortAscendingOperationText: 'clic para orden ascendente',
    sortDescendingOperationText: 'clic para orden descendente',
    sortCancelOperationText: 'clic para cancelar la ordenación',
    clearFilterResultButtonText: 'Limpiar',
    columnConfigButtonText: 'Configurar columnas',
    columnConfigTitleText: 'Configuración de columnas',
    columnConfigDescriptionText: 'Selecciona las columnas que quieres mostrar',
    confirmText: 'Confirmar',
    cancelText: 'Cancelar',
    resetText: 'Restablecer',
    selectAllText: 'Seleccionar todo',
    searchResultText:
      'Búsqueda «{result}»: sin elementos. | Búsqueda «{result}»: 1 elemento. | Búsqueda «{result}»: {count} elementos.',
  },
  select: {
    ...enUS.select,
    empty: 'Sin datos',
    loadingText: 'cargando…',
    placeholder: 'selecciona',
  },
  empty: {
    ...enUS.empty,
    titleText: {
      maintenance: 'En construcción',
      success: 'Correcto',
      fail: 'Error',
      empty: 'Sin datos',
      networkError: 'Error de red',
    },
  },
  dialog: { confirm: 'Confirmar', cancel: 'Cancelar' },
  drawer: { confirm: 'Confirmar', cancel: 'Cancelar' },
  popconfirm: { confirm: { content: 'Aceptar' }, cancel: { content: 'Cancelar' } },
  colorPicker: {
    ...enUS.colorPicker,
    swatchColorTitle: 'Predeterminado del sistema',
    recentColorTitle: 'Usados recientemente',
    clearConfirmText: '¿Borrar los colores usados recientemente?',
    singleColor: 'Único',
    gradientColor: 'Degradado',
  },
  input: { ...enUS.input, placeholder: 'introduce un valor' },
}
