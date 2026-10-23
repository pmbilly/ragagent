/**
 * SSE 错误帧的收束判定。
 *
 * 服务端把**工具调用失败**也编码成 `responseType=error`，但那是单次调用失败：
 * done=false、metadata 带 toolName，智能体会继续重试/收尾并最终给出回答。
 * 只有终态错误（done=true，例如整轮 QA 抛错）才应该终止整条流——
 * 早期实现见 error 就 abort，导致后续步骤和最终答案全部丢失，界面永久“正在思考”
 * （后端日志表现为 1~2 秒后的 Connection closed，cause=AsyncRequestNotUsableException）。
 */

export interface StreamErrorFrameLike {
  responseType?: string
  done?: boolean
  data?: Record<string, unknown> | null
}

export function shouldStopStreamOnErrorFrame(frame: StreamErrorFrameLike | null | undefined): boolean {
  if (!frame || frame.responseType !== 'error') return false
  return frame.done === true
}
